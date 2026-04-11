package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.item.ClaimWandHandler;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClaimVisualizationManager {
	private static final int VISUAL_TICK_INTERVAL = 8;
	private static final int OUTLINE_STEP = 4;
	private static final int LOCAL_VERTICAL_SEARCH = 6;
	private static final Identifier WAND_REACH_MODIFIER = Identifier.fromNamespaceAndPath("safe-zone", "wand_reach");

	private static final BlockState PREVIEW_EDGE = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
	private static final BlockState PREVIEW_CORNER = Blocks.GOLD_BLOCK.defaultBlockState();
	private static final BlockState OWNER_EDGE = Blocks.LIME_STAINED_GLASS.defaultBlockState();
	private static final BlockState TRUSTED_EDGE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
	private static final BlockState BLOCKED_EDGE = Blocks.RED_STAINED_GLASS.defaultBlockState();
	private static final BlockState ADMIN_EDGE = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();

	private final ClaimManager claimManager;
	private final ClaimWandHandler claimWandHandler;
	private final Map<UUID, Map<BlockPos, BlockState>> activePreviews = new HashMap<>();

	public ClaimVisualizationManager(ClaimManager claimManager, ClaimWandHandler claimWandHandler) {
		this.claimManager = claimManager;
		this.claimWandHandler = claimWandHandler;
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % VISUAL_TICK_INTERVAL != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshPlayer(player);
		}
	}

	public void clearPlayer(ServerPlayer player) {
		removeWandReach(player);
		applyPreview(player, Map.of());
	}

	/**
	 * In creative mode, {@code UseItemCallback} may not fire for items with no active use action
	 * (such as the golden hoe). Extending {@code BLOCK_INTERACTION_RANGE} syncs the client's
	 * crosshair reach so the client sends a {@code UseItemOnPacket} for distant blocks, which
	 * triggers {@code UseBlockCallback} normally.
	 *
	 * In survival (and other modes), {@code UseItemCallback} fires when no block is in vanilla
	 * reach, so no attribute change is needed — and avoiding it prevents any risk of interfering
	 * with vanilla block interaction distance validation.
	 */
	private void applyWandReach(ServerPlayer player) {
		if (!player.isCreative()) {
			removeWandReach(player);
			return;
		}
		var attr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (attr == null) return;
		double range = this.claimManager.getGameplayConfig().effectiveWandSelectionRange();
		double delta = range - attr.getBaseValue();
		if (delta <= 0) {
			attr.removeModifier(WAND_REACH_MODIFIER);
			return;
		}
		if (attr.hasModifier(WAND_REACH_MODIFIER)) {
			AttributeModifier existing = attr.getModifier(WAND_REACH_MODIFIER);
			if (existing != null && existing.amount() == delta) {
				return;
			}
		}
		attr.addOrUpdateTransientModifier(new AttributeModifier(WAND_REACH_MODIFIER, delta, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void removeWandReach(ServerPlayer player) {
		var attr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (attr != null) {
			attr.removeModifier(WAND_REACH_MODIFIER);
		}
	}

	public void refreshPlayer(ServerPlayer player) {
		Map<BlockPos, BlockState> desiredPreview = new HashMap<>();
		Component overlayMessage = null;
		if (!this.claimWandHandler.isClaimWand(player.getMainHandItem())) {
			this.claimWandHandler.cancelIfNoLongerHoldingWand(player);
			removeWandReach(player);
			applyPreview(player, desiredPreview);
			return;
		}
		applyWandReach(player);

		ServerLevel level = (ServerLevel) player.level();
		BlockPos playerPos = player.blockPosition();
		var resizeState = this.claimWandHandler.getResizeState(player.getUUID());
		var selectedFirstCorner = this.claimWandHandler.getFirstCorner(player.getUUID());
		if (resizeState.isPresent()) {
			BlockPos previewTarget = resolvePreviewTarget(player);
			var validation = this.claimManager.validateResizedClaim(resizeState.get().claimId(), player.level(), player.getUUID(),
				resizeState.get().fixedCorner(), previewTarget);
			addPreview(desiredPreview,
				resizeState.get().fixedCorner(),
				previewTarget,
				PREVIEW_EDGE,
				PREVIEW_CORNER,
				level,
				createPreviewContext(player, previewTarget));
			addConflictingClaimPreview(desiredPreview, validation.conflictingClaim(), level, createPreviewContext(player, previewTarget));
			overlayMessage = buildResizePreviewMessage(resizeState.get(), previewTarget, validation);
		} else if (selectedFirstCorner.isPresent()) {
			BlockPos previewTarget = resolvePreviewTarget(player);
			var validation = this.claimManager.validateNewClaim(player.level(), player.getUUID(), selectedFirstCorner.get(), previewTarget);
			addPreview(desiredPreview,
				selectedFirstCorner.get(),
				previewTarget,
				PREVIEW_EDGE,
				PREVIEW_CORNER,
				level,
				createPreviewContext(player, previewTarget));
			addConflictingClaimPreview(desiredPreview, validation.conflictingClaim(), level, createPreviewContext(player, previewTarget));
			overlayMessage = buildPreviewMessage(selectedFirstCorner.get(), previewTarget, validation);
		} else {
			var claim = this.claimManager.getClaimAt(playerPos);
			if (claim.isPresent()) {
				PermissionResult permission = this.claimManager.canBuild(player, playerPos);
				boolean pendingRemoval = permission == PermissionResult.OWNER
					&& this.claimWandHandler.isPendingRemoval(player.getUUID(), claim.get().claimId);
				BlockPos anchorTarget = resolvePreviewTarget(player);
				addPreview(desiredPreview,
					new BlockPos(claim.get().getMinX(), playerPos.getY(), claim.get().getMinZ()),
					new BlockPos(claim.get().getMaxX(), playerPos.getY(), claim.get().getMaxZ()),
					pendingRemoval ? BLOCKED_EDGE : permissionEdge(permission),
					PREVIEW_CORNER,
					level,
					createPreviewContext(player, anchorTarget));
				overlayMessage = buildClaimOverlay(claim.get(), permission, pendingRemoval);
			}
		}

		applyPreview(player, desiredPreview);
		if (overlayMessage != null) {
			player.connection.send(new ClientboundSetActionBarTextPacket(overlayMessage));
		}
	}

	private static void addPreview(Map<BlockPos, BlockState> desiredPreview, BlockPos firstCorner, BlockPos secondCorner,
		BlockState edgeState, BlockState cornerState, ServerLevel level, PreviewContext context) {
		int minX = Math.min(firstCorner.getX(), secondCorner.getX());
		int maxX = Math.max(firstCorner.getX(), secondCorner.getX());
		int minZ = Math.min(firstCorner.getZ(), secondCorner.getZ());
		int maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ());

		for (int x = minX; x <= maxX; x += OUTLINE_STEP) {
			addMarker(desiredPreview, level, context, x, minZ, edgeState);
			addMarker(desiredPreview, level, context, x, maxZ, edgeState);
		}
		for (int z = minZ; z <= maxZ; z += OUTLINE_STEP) {
			addMarker(desiredPreview, level, context, minX, z, edgeState);
			addMarker(desiredPreview, level, context, maxX, z, edgeState);
		}

		addMarker(desiredPreview, level, context, minX, minZ, cornerState);
		addMarker(desiredPreview, level, context, minX, maxZ, cornerState);
		addMarker(desiredPreview, level, context, maxX, minZ, cornerState);
		addMarker(desiredPreview, level, context, maxX, maxZ, cornerState);
	}

	private static void addMarker(Map<BlockPos, BlockState> desiredPreview, ServerLevel level, PreviewContext context,
		int x, int z, BlockState state) {
		BlockPos anchorPos = resolveAnchor(level, context, x, z);
		if (anchorPos != null) {
			desiredPreview.put(anchorPos, state);
		}
	}

	private static void addConflictingClaimPreview(Map<BlockPos, BlockState> desiredPreview, ClaimData conflictingClaim,
		ServerLevel level, PreviewContext context) {
		if (conflictingClaim == null) {
			return;
		}

		addPreview(desiredPreview,
			new BlockPos(conflictingClaim.getMinX(), context.referenceY(), conflictingClaim.getMinZ()),
			new BlockPos(conflictingClaim.getMaxX(), context.referenceY(), conflictingClaim.getMaxZ()),
			BLOCKED_EDGE,
			BLOCKED_EDGE,
			level,
			context);
	}

	private void applyPreview(ServerPlayer player, Map<BlockPos, BlockState> desiredPreview) {
		Map<BlockPos, BlockState> previousPreview = this.activePreviews.getOrDefault(player.getUUID(), Map.of());

		for (Map.Entry<BlockPos, BlockState> previousEntry : previousPreview.entrySet()) {
			BlockPos pos = previousEntry.getKey();
			BlockState desiredState = desiredPreview.get(pos);
			if (desiredState == null) {
				sendBlockUpdate(player, pos, player.level().getBlockState(pos));
			} else if (!desiredState.equals(previousEntry.getValue())) {
				sendBlockUpdate(player, pos, desiredState);
			}
		}

		for (Map.Entry<BlockPos, BlockState> desiredEntry : desiredPreview.entrySet()) {
			if (!previousPreview.containsKey(desiredEntry.getKey())) {
				sendBlockUpdate(player, desiredEntry.getKey(), desiredEntry.getValue());
			}
		}

		if (desiredPreview.isEmpty()) {
			this.activePreviews.remove(player.getUUID());
		} else {
			this.activePreviews.put(player.getUUID(), new HashMap<>(desiredPreview));
		}
	}

	private static void sendBlockUpdate(ServerPlayer player, BlockPos pos, BlockState state) {
		player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
	}

	private Component buildPreviewMessage(BlockPos firstCorner, BlockPos secondCorner, ClaimValidationResult validation) {
		int width = Math.abs(secondCorner.getX() - firstCorner.getX()) + 1;
		int depth = Math.abs(secondCorner.getZ() - firstCorner.getZ()) + 1;
		MutableComponent message = Component.literal(SafeZoneText.newAreaLabel()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(width + "x" + depth).withStyle(ChatFormatting.AQUA))
			.append(separator());
		if (validation.isAllowed()) {
			return message
				.append(Component.literal(SafeZoneText.finishSelection()).withStyle(ChatFormatting.GREEN))
				.append(separator())
				.append(Component.literal(SafeZoneText.cancelSelection()).withStyle(ChatFormatting.GRAY));
		}

		return message.append(validationStatus(validation));
	}

	private Component buildResizePreviewMessage(ClaimWandHandler.ResizeState resizeState, BlockPos newCorner,
		ClaimValidationResult validation) {
		int width = Math.abs(newCorner.getX() - resizeState.fixedCorner().getX()) + 1;
		int depth = Math.abs(newCorner.getZ() - resizeState.fixedCorner().getZ()) + 1;
		MutableComponent message = Component.literal(SafeZoneText.resizeLabel()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(resizeState.claimId()).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(width + "x" + depth).withStyle(ChatFormatting.AQUA))
			.append(separator());
		if (validation.isAllowed()) {
			return message
				.append(Component.literal(SafeZoneText.finishSelection()).withStyle(ChatFormatting.GREEN))
				.append(separator())
				.append(Component.literal(SafeZoneText.cancelSelection()).withStyle(ChatFormatting.GRAY));
		}

		return message.append(validationStatus(validation));
	}

	private static Component buildClaimOverlay(ClaimData claim, PermissionResult permission, boolean pendingRemoval) {
		String claimSummary = claim.claimId + " " + claim.getWidth() + "x" + claim.getDepth();
		if (pendingRemoval) {
			return Component.literal(SafeZoneText.removingLabel()).withStyle(ChatFormatting.RED)
				.append(Component.literal(claimSummary).withStyle(ChatFormatting.YELLOW))
				.append(separator())
				.append(Component.literal(SafeZoneText.removeConfirmStep()).withStyle(ChatFormatting.GRAY));
		}

		return switch (permission) {
			case OWNER -> Component.literal(SafeZoneText.ownerClaimPrefix()).withStyle(ChatFormatting.GREEN)
				.append(Component.literal(claimSummary).withStyle(ChatFormatting.YELLOW))
				.append(separator())
				.append(Component.literal(SafeZoneText.ownerResizeHint()).withStyle(ChatFormatting.AQUA))
				.append(separator())
				.append(Component.literal(SafeZoneText.ownerRemoveHint()).withStyle(ChatFormatting.GRAY));
			case TRUSTED -> Component.literal(claim.ownerName + " ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(claimSummary).withStyle(ChatFormatting.YELLOW))
				.append(separator())
				.append(Component.literal(SafeZoneText.trustedCanBuild()).withStyle(ChatFormatting.GREEN));
			case ADMIN_BYPASS -> Component.literal(SafeZoneText.adminViewPrefix()).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(SafeZoneText.adminViewLabel()).withStyle(ChatFormatting.YELLOW))
				.append(separator())
				.append(Component.literal(claim.ownerName + " ").withStyle(ChatFormatting.AQUA))
				.append(Component.literal(claimSummary).withStyle(ChatFormatting.YELLOW));
			case DENIED -> Component.literal(claim.ownerName + " ").withStyle(ChatFormatting.RED)
				.append(Component.literal(claimSummary).withStyle(ChatFormatting.YELLOW))
				.append(separator())
				.append(Component.literal(SafeZoneText.deniedBuildHint()).withStyle(ChatFormatting.GRAY));
		};
	}

	private MutableComponent validationStatus(ClaimValidationResult validation) {
		var gameplayConfig = this.claimManager.getGameplayConfig();
		return switch (validation.failure()) {
			case DIMENSION_NOT_ALLOWED -> Component.literal(SafeZoneText.validationOverworldOnly()).withStyle(ChatFormatting.RED);
			case CLAIM_LIMIT_REACHED -> Component.literal(SafeZoneText.validationClaimLimitReached()).withStyle(ChatFormatting.RED);
			case CLAIM_TOO_LARGE -> Component.literal(SafeZoneText.validationTooBig(gameplayConfig.maxClaimWidth, gameplayConfig.maxClaimDepth))
				.withStyle(ChatFormatting.RED);
			case TOO_CLOSE_TO_EXISTING_CLAIM -> Component.literal(SafeZoneText.validationTooCloseTo(conflictingClaimName(validation.conflictingClaim())))
				.withStyle(ChatFormatting.RED);
		};
	}

	private static MutableComponent separator() {
		return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY);
	}

	private static String conflictingClaimName(ClaimData claim) {
		if (claim == null) {
			return SafeZoneText.genericConflictName();
		}
		return claim.ownerName + " (" + claim.claimId + ")";
	}

	private BlockPos resolvePreviewTarget(ServerPlayer player) {
		double range = this.claimManager.getGameplayConfig().effectiveWandSelectionRange();
		HitResult hitResult = player.pick(range, 0.0F, false);
		if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
			return blockHitResult.getBlockPos();
		}
		return player.blockPosition();
	}

	private static PreviewContext createPreviewContext(ServerPlayer player, BlockPos focusPos) {
		ServerLevel level = (ServerLevel) player.level();
		return new PreviewContext(shouldUseSurfaceAnchors(level, player, focusPos), focusPos.getY());
	}

	private static boolean shouldUseSurfaceAnchors(ServerLevel level, ServerPlayer player, BlockPos focusPos) {
		if (level.canSeeSky(player.blockPosition().above()) || level.canSeeSky(focusPos.above())) {
			return true;
		}

		int surfaceAnchorY = getSurfaceAnchorY(level, player.getBlockX(), player.getBlockZ());
		return surfaceAnchorY >= level.getMinY() && player.getY() >= surfaceAnchorY + 4;
	}

	private static BlockPos resolveAnchor(ServerLevel level, PreviewContext context, int x, int z) {
		if (context.useSurfaceAnchors()) {
			int surfaceY = getSurfaceAnchorY(level, x, z);
			if (surfaceY < level.getMinY()) {
				return null;
			}
			return resolveVisiblePreviewPos(level, new BlockPos(x, surfaceY, z));
		}

		int baseY = Math.max(level.getMinY(), Math.min(context.referenceY(), level.getMaxY() - 1));
		for (int offset = 0; offset <= LOCAL_VERTICAL_SEARCH; offset++) {
			int belowY = baseY - offset;
			if (belowY >= level.getMinY()) {
				BlockPos belowPos = new BlockPos(x, belowY, z);
				if (isSolidAnchor(level, belowPos)) {
					return resolveVisiblePreviewPos(level, belowPos);
				}
			}

			if (offset == 0) {
				continue;
			}

			int aboveY = baseY + offset;
			if (aboveY < level.getMaxY()) {
				BlockPos abovePos = new BlockPos(x, aboveY, z);
				if (isSolidAnchor(level, abovePos)) {
					return resolveVisiblePreviewPos(level, abovePos);
				}
			}
		}

		return null;
	}

	private static BlockPos resolveVisiblePreviewPos(ServerLevel level, BlockPos supportPos) {
		BlockPos coverPos = supportPos.above();
		if (coverPos.getY() >= level.getMaxY()) {
			return supportPos;
		}

		BlockState coverState = level.getBlockState(coverPos);
		if (isSnowCover(coverState)) {
			return coverPos;
		}

		return supportPos;
	}

	private static boolean isSnowCover(BlockState state) {
		return state.is(Blocks.SNOW);
	}

	private static int getSurfaceAnchorY(ServerLevel level, int x, int z) {
		int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return Math.max(height, level.getMinY() - 1);
	}

	private static boolean isSolidAnchor(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.isSolidRender() && state.isFaceSturdy(level, pos, Direction.UP);
	}

	private static BlockState permissionEdge(PermissionResult permission) {
		return switch (permission) {
			case OWNER -> OWNER_EDGE;
			case TRUSTED -> TRUSTED_EDGE;
			case ADMIN_BYPASS -> ADMIN_EDGE;
			case DENIED -> BLOCKED_EDGE;
		};
	}

	private record PreviewContext(boolean useSurfaceAnchors, int referenceY) {
	}
}
