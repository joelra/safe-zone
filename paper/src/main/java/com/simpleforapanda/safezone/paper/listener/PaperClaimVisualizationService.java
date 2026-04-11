package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.ClaimValidationResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperClaimVisualizationService implements Listener {
	private static final NamespacedKey WAND_REACH_KEY = new NamespacedKey("safe-zone", "wand_reach");
	private static final int VISUAL_TICK_INTERVAL = 8;
	private static final int LOCAL_VERTICAL_SEARCH = 6;

	private static final BlockData PREVIEW_EDGE = Material.YELLOW_STAINED_GLASS.createBlockData();
	private static final BlockData PREVIEW_CORNER = Material.GOLD_BLOCK.createBlockData();
	private static final BlockData OWNER_EDGE = Material.LIME_STAINED_GLASS.createBlockData();
	private static final BlockData TRUSTED_EDGE = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
	private static final BlockData BLOCKED_EDGE = Material.RED_STAINED_GLASS.createBlockData();
	private static final BlockData ADMIN_EDGE = Material.ORANGE_STAINED_GLASS.createBlockData();

	private final JavaPlugin plugin;
	private final PaperRuntime runtime;
	private final PaperClaimWandState wandState;
	private final Map<UUID, Map<PreviewBlockPos, BlockData>> activePreviews = new ConcurrentHashMap<>();
	private final Map<UUID, ConfirmedClaim> activeConfirmations = new ConcurrentHashMap<>();
	private BukkitTask task;

	public PaperClaimVisualizationService(JavaPlugin plugin, PaperRuntime runtime, PaperClaimWandState wandState) {
		this.plugin = plugin;
		this.runtime = runtime;
		this.wandState = wandState;
	}

	public void start() {
		if (this.task != null) {
			return;
		}
		this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, VISUAL_TICK_INTERVAL, VISUAL_TICK_INTERVAL);
	}

	public void stop() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
		clearAll();
	}

	public void clearAll() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			clearPlayer(player);
		}
		this.activePreviews.clear();
		this.activeConfirmations.clear();
	}

	public void clearPlayer(Player player) {
		removeWandReach(player);
		this.activeConfirmations.remove(player.getUniqueId());
		applyPreview(player, Map.of());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		removeWandReach(event.getPlayer());
		UUID playerId = event.getPlayer().getUniqueId();
		this.activePreviews.remove(playerId);
		this.activeConfirmations.remove(playerId);
	}

	private void applyWandReach(Player player, SafeZoneConfig config) {
		var attr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
		if (attr == null) return;
		double range = config.gameplay.effectiveWandSelectionRange();
		double delta = range - attr.getBaseValue();
		if (delta <= 0) {
			attr.removeModifier(WAND_REACH_KEY);
			return;
		}
		for (AttributeModifier mod : attr.getModifiers()) {
			if (mod.getKey().equals(WAND_REACH_KEY)) {
				if (mod.getAmount() == delta) return;
				attr.removeModifier(WAND_REACH_KEY);
				break;
			}
		}
		attr.addModifier(new AttributeModifier(WAND_REACH_KEY, delta, AttributeModifier.Operation.ADD_NUMBER));
	}

	private static void removeWandReach(Player player) {
		var attr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
		if (attr != null) {
			attr.removeModifier(WAND_REACH_KEY);
		}
	}

	private void tick() {
		SafeZoneConfig config = this.runtime.services().configService().current();
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		for (Player player : Bukkit.getOnlinePlayers()) {
			refreshPlayer(player, config, claimStore);
		}
	}

	public void refreshPlayer(Player player) {
		refreshPlayer(player, this.runtime.services().configService().current(), this.runtime.services().claimStore());
	}

	private static void addPreview(
		Map<PreviewBlockPos, BlockData> desiredPreview,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner,
		BlockData edgeState,
		BlockData cornerState,
		World world,
		PreviewContext context,
		int maxOutlineStep
	) {
		int minX = Math.min(firstCorner.x(), secondCorner.x());
		int maxX = Math.max(firstCorner.x(), secondCorner.x());
		int minZ = Math.min(firstCorner.z(), secondCorner.z());
		int maxZ = Math.max(firstCorner.z(), secondCorner.z());

		// Scale the step so small claims show intermediate glass markers between corners
		int xStep = Math.max(1, Math.min(maxOutlineStep, (maxX - minX) / 2));
		int zStep = Math.max(1, Math.min(maxOutlineStep, (maxZ - minZ) / 2));

		for (int x = minX; x <= maxX; x += xStep) {
			addMarker(desiredPreview, world, context, x, minZ, edgeState);
			addMarker(desiredPreview, world, context, x, maxZ, edgeState);
		}
		for (int z = minZ; z <= maxZ; z += zStep) {
			addMarker(desiredPreview, world, context, minX, z, edgeState);
			addMarker(desiredPreview, world, context, maxX, z, edgeState);
		}

		addMarker(desiredPreview, world, context, minX, minZ, cornerState);
		addMarker(desiredPreview, world, context, minX, maxZ, cornerState);
		addMarker(desiredPreview, world, context, maxX, minZ, cornerState);
		addMarker(desiredPreview, world, context, maxX, maxZ, cornerState);
	}

	private static void addMarker(
		Map<PreviewBlockPos, BlockData> desiredPreview,
		World world,
		PreviewContext context,
		int x,
		int z,
		BlockData state
	) {
		PreviewBlockPos anchorPos = resolveAnchor(world, context, x, z);
		if (anchorPos != null) {
			desiredPreview.put(anchorPos, state);
		}
	}

	private static void addConflictingClaimPreview(
		Map<PreviewBlockPos, BlockData> desiredPreview,
		ClaimData conflictingClaim,
		World world,
		PreviewContext context,
		int maxOutlineStep
	) {
		if (conflictingClaim == null) {
			return;
		}

		addPreview(desiredPreview,
			new ClaimCoordinates(conflictingClaim.getMinX(), context.referenceY(), conflictingClaim.getMinZ()),
			new ClaimCoordinates(conflictingClaim.getMaxX(), context.referenceY(), conflictingClaim.getMaxZ()),
			BLOCKED_EDGE,
			BLOCKED_EDGE,
			world,
			context,
			maxOutlineStep);
	}

	private void applyPreview(Player player, Map<PreviewBlockPos, BlockData> desiredPreview) {
		Map<PreviewBlockPos, BlockData> previousPreview = this.activePreviews.getOrDefault(player.getUniqueId(), Map.of());

		for (Map.Entry<PreviewBlockPos, BlockData> previousEntry : previousPreview.entrySet()) {
			PreviewBlockPos pos = previousEntry.getKey();
			BlockData desiredState = desiredPreview.get(pos);
			if (desiredState == null) {
				sendBlockChange(player, pos, actualBlockData(pos));
			} else if (!desiredState.matches(previousEntry.getValue())) {
				sendBlockChange(player, pos, desiredState);
			}
		}

		for (Map.Entry<PreviewBlockPos, BlockData> desiredEntry : desiredPreview.entrySet()) {
			if (!previousPreview.containsKey(desiredEntry.getKey())) {
				sendBlockChange(player, desiredEntry.getKey(), desiredEntry.getValue());
			}
		}

		if (desiredPreview.isEmpty()) {
			this.activePreviews.remove(player.getUniqueId());
		} else {
			this.activePreviews.put(player.getUniqueId(), new HashMap<>(desiredPreview));
		}
	}

	private static BlockData actualBlockData(PreviewBlockPos pos) {
		World world = Bukkit.getWorld(pos.worldId());
		return world == null ? Material.AIR.createBlockData() : world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData();
	}

	private static void sendBlockChange(Player player, PreviewBlockPos pos, BlockData data) {
		World world = Bukkit.getWorld(pos.worldId());
		if (world == null) {
			return;
		}
		player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()), data);
	}

	private static Component buildPreviewMessage(
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner,
		ClaimValidationResult validation,
		SafeZoneConfig config
	) {
		int width = Math.abs(secondCorner.x() - firstCorner.x()) + 1;
		int depth = Math.abs(secondCorner.z() - firstCorner.z()) + 1;
		Component message = text("New area ", GOLD)
			.append(text(width + "x" + depth, AQUA))
			.append(separator());
		if (validation.isAllowed()) {
			return message
				.append(text("Right-click to finish", GREEN))
				.append(separator())
				.append(text("Shift + right-click to cancel", GRAY));
		}

		return message.append(validationStatus(validation, config));
	}

	private static Component buildResizePreviewMessage(
		PaperClaimWandState.ResizeState resizeState,
		ClaimCoordinates newCorner,
		ClaimValidationResult validation,
		SafeZoneConfig config
	) {
		int width = Math.abs(newCorner.x() - resizeState.fixedCorner().x()) + 1;
		int depth = Math.abs(newCorner.z() - resizeState.fixedCorner().z()) + 1;
		Component message = text("Resize ", GOLD)
			.append(text(resizeState.claimId(), YELLOW))
			.append(text(" ", DARK_GRAY))
			.append(text(width + "x" + depth, AQUA))
			.append(separator());
		if (validation.isAllowed()) {
			return message
				.append(text("Right-click to finish", GREEN))
				.append(separator())
				.append(text("Shift + right-click to cancel", GRAY));
		}

		return message.append(validationStatus(validation, config));
	}

	private static Component buildClaimOverlay(ClaimData claim, PermissionResult permission, boolean pendingRemoval) {
		String claimSummary = claim.claimId + " " + claim.getWidth() + "x" + claim.getDepth();
		if (pendingRemoval) {
			return text("Removing ", RED)
				.append(text(claimSummary, YELLOW))
				.append(separator())
				.append(text("Shift + right-click again to confirm", GRAY));
		}

		return switch (permission) {
			case OWNER -> text("Your ", GREEN)
				.append(text(claimSummary, YELLOW))
				.append(separator())
				.append(text("Right-click a corner to resize", AQUA))
				.append(separator())
				.append(text("Shift + right-click inside to remove", GRAY));
			case TRUSTED -> text(claim.ownerName + " ", AQUA)
				.append(text(claimSummary, YELLOW))
				.append(separator())
				.append(text("You can build here", GREEN));
			case ADMIN_BYPASS -> text("Admin ", GOLD)
				.append(text("view", YELLOW))
				.append(separator())
				.append(text(claim.ownerName + " ", AQUA))
				.append(text(claimSummary, YELLOW));
			case DENIED -> text(claim.ownerName + " ", RED)
				.append(text(claimSummary, YELLOW))
				.append(separator())
				.append(text("You cannot build here", GRAY));
		};
	}

	private static Component validationStatus(ClaimValidationResult validation, SafeZoneConfig config) {
		return switch (validation.failure()) {
			case DIMENSION_NOT_ALLOWED -> text("Overworld only", RED);
			case CLAIM_LIMIT_REACHED -> text("Claim limit reached", RED);
			case CLAIM_TOO_LARGE -> text(
				"Too big (max " + config.gameplay.maxClaimWidth + "x" + config.gameplay.maxClaimDepth + ")",
				RED);
			case TOO_CLOSE_TO_EXISTING_CLAIM -> text(
				"Too close to " + conflictingClaimName(validation.conflictingClaim()),
				RED);
		};
	}

	private static String conflictingClaimName(ClaimData claim) {
		return claim == null ? "another area" : claim.ownerName + " (" + claim.claimId + ")";
	}

	private static Component separator() {
		return text(" • ", DARK_GRAY);
	}

	private static ClaimCoordinates resolvePreviewTarget(Player player, int rangeBlocks) {
		Block targetBlock = player.getTargetBlockExact(rangeBlocks);
		Block referenceBlock = targetBlock != null ? targetBlock : player.getLocation().getBlock();
		return new ClaimCoordinates(referenceBlock.getX(), referenceBlock.getY(), referenceBlock.getZ());
	}

	private static PreviewContext createPreviewContext(Player player, ClaimCoordinates focusPos) {
		return new PreviewContext(shouldUseSurfaceAnchors(player.getWorld(), player, focusPos), focusPos.y());
	}

	private static boolean shouldUseSurfaceAnchors(World world, Player player, ClaimCoordinates focusPos) {
		int playerSurfaceY = getSurfaceAnchorY(world, player.getLocation().getBlockX(), player.getLocation().getBlockZ());
		if (playerSurfaceY >= world.getMinHeight() && player.getLocation().getBlockY() >= playerSurfaceY + 4) {
			return true;
		}

		int focusSurfaceY = getSurfaceAnchorY(world, focusPos.x(), focusPos.z());
		return focusSurfaceY >= world.getMinHeight() && focusPos.y() >= focusSurfaceY + 4;
	}

	private static PreviewBlockPos resolveAnchor(World world, PreviewContext context, int x, int z) {
		if (context.useSurfaceAnchors()) {
			int surfaceY = getSurfaceAnchorY(world, x, z);
			if (surfaceY < world.getMinHeight()) {
				return null;
			}
			return resolveVisiblePreviewPos(world, x, surfaceY, z);
		}

		int baseY = Math.max(world.getMinHeight(), Math.min(context.referenceY(), world.getMaxHeight() - 1));
		for (int offset = 0; offset <= LOCAL_VERTICAL_SEARCH; offset++) {
			int belowY = baseY - offset;
			if (belowY >= world.getMinHeight() && isSolidAnchor(world.getBlockAt(x, belowY, z))) {
				return resolveVisiblePreviewPos(world, x, belowY, z);
			}

			if (offset == 0) {
				continue;
			}

			int aboveY = baseY + offset;
			if (aboveY < world.getMaxHeight() && isSolidAnchor(world.getBlockAt(x, aboveY, z))) {
				return resolveVisiblePreviewPos(world, x, aboveY, z);
			}
		}

		return null;
	}

	private static PreviewBlockPos resolveVisiblePreviewPos(World world, int x, int supportY, int z) {
		int coverY = supportY + 1;
		if (coverY >= world.getMaxHeight()) {
			return new PreviewBlockPos(world.getUID(), x, supportY, z);
		}

		Block coverBlock = world.getBlockAt(x, coverY, z);
		if (isSnowCover(coverBlock)) {
			return new PreviewBlockPos(world.getUID(), x, coverY, z);
		}

		return new PreviewBlockPos(world.getUID(), x, supportY, z);
	}

	private static boolean isSnowCover(Block block) {
		return block.getType() == Material.SNOW;
	}

	private void refreshPlayer(Player player, SafeZoneConfig config, PaperClaimStore claimStore) {
		Map<PreviewBlockPos, BlockData> desiredPreview = new HashMap<>();
		Component overlayMessage = null;
		if (!PaperClaimWandSupport.isClaimWand(player.getInventory().getItemInMainHand(), config.gameplay)) {
			this.wandState.cancelIfNoLongerHoldingWand(player, config.gameplay);
			removeWandReach(player);
			applyPreview(player, desiredPreview);
			return;
		}
		applyWandReach(player, config);

		// Consume any just-completed claim so it shows immediately for wandConfirmDisplaySeconds
		this.wandState.takeConfirmation(player.getUniqueId()).ifPresent(claim ->
			this.activeConfirmations.put(player.getUniqueId(),
				new ConfirmedClaim(claim, System.currentTimeMillis() + config.gameplay.wandConfirmDisplayMillis())));

		int selectionRange = config.gameplay.wandSelectionRangeBlocks;
		int outlineStep = config.gameplay.wandOutlineStep;
		ClaimCoordinates lookTarget = resolvePreviewTarget(player, selectionRange);

		Optional<PaperClaimWandState.ResizeState> resizeState = this.wandState.getResizeState(player.getUniqueId());
		Optional<PaperClaimWandState.PendingSelection> firstSelection = this.wandState.getFirstCorner(player.getUniqueId());
		if (resizeState.isPresent()) {
			ClaimValidationResult validation = claimStore.validateResizedClaim(
				player.getWorld(),
				player.getUniqueId(),
				resizeState.get().claimId(),
				resizeState.get().fixedCorner(),
				lookTarget);
			PreviewContext resizeContext = createPreviewContext(player, lookTarget);
			addPreview(desiredPreview,
				resizeState.get().fixedCorner(),
				lookTarget,
				PREVIEW_EDGE,
				PREVIEW_CORNER,
				player.getWorld(),
				resizeContext,
				outlineStep);
			addConflictingClaimPreview(desiredPreview, validation.conflictingClaim(), player.getWorld(), resizeContext, outlineStep);
			overlayMessage = buildResizePreviewMessage(resizeState.get(), lookTarget, validation, config);
		} else if (firstSelection.isPresent()) {
			ClaimValidationResult validation = claimStore.validateNewClaim(
				player.getWorld(),
				player.getUniqueId(),
				firstSelection.get().corner(),
				lookTarget);
			PreviewContext selectionContext = createPreviewContext(player, lookTarget);
			addPreview(desiredPreview,
				firstSelection.get().corner(),
				lookTarget,
				PREVIEW_EDGE,
				PREVIEW_CORNER,
				player.getWorld(),
				selectionContext,
				outlineStep);
			addConflictingClaimPreview(desiredPreview, validation.conflictingClaim(), player.getWorld(), selectionContext, outlineStep);
			overlayMessage = buildPreviewMessage(firstSelection.get().corner(), lookTarget, validation, config);
		} else {
			// Check active confirmation first (just created/resized a claim)
			ConfirmedClaim confirmation = this.activeConfirmations.get(player.getUniqueId());
			if (confirmation != null && System.currentTimeMillis() > confirmation.expiresAt()) {
				this.activeConfirmations.remove(player.getUniqueId());
				confirmation = null;
			}

			Optional<ClaimData> claim = confirmation != null
				? Optional.of(confirmation.claim())
				: claimStore.getClaimAt(player.getLocation());

			// Fall back to the block the player is looking at (covers post-creation and wand-inspection)
			if (claim.isEmpty()) {
				claim = claimStore.getClaimAt(new Location(player.getWorld(), lookTarget.x(), lookTarget.y(), lookTarget.z()));
			}

			if (claim.isPresent()) {
				// Use player's block position for the context anchor so corner heights are stable as the player looks around
				ClaimCoordinates playerCoords = new ClaimCoordinates(
					player.getLocation().getBlockX(),
					player.getLocation().getBlockY(),
					player.getLocation().getBlockZ());
				PreviewContext context = createPreviewContext(player, playerCoords);
				PermissionResult permission = claimStore.getPermission(
					claim.get(),
					player.getUniqueId(),
					player.hasPermission("safezone.command.admin"));
				if (confirmation != null) {
					// Player just claimed this — they own it regardless of standing position
					permission = PermissionResult.OWNER;
				}
				boolean pendingRemoval = permission == PermissionResult.OWNER
					&& this.wandState.isPendingRemoval(player.getUniqueId(), claim.get().claimId);
				addPreview(desiredPreview,
					new ClaimCoordinates(claim.get().getMinX(), player.getLocation().getBlockY(), claim.get().getMinZ()),
					new ClaimCoordinates(claim.get().getMaxX(), player.getLocation().getBlockY(), claim.get().getMaxZ()),
					pendingRemoval ? BLOCKED_EDGE : permissionEdge(permission),
					PREVIEW_CORNER,
					player.getWorld(),
					context,
					outlineStep);
				overlayMessage = buildClaimOverlay(claim.get(), permission, pendingRemoval);
			}
		}

		applyPreview(player, desiredPreview);
		if (overlayMessage != null) {
			player.sendActionBar(overlayMessage);
		}
	}

	private static int getSurfaceAnchorY(World world, int x, int z) {
		return world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY();
	}

	private static boolean isSolidAnchor(Block block) {
		return !block.isEmpty() && block.getType().isSolid();
	}

	private static BlockData permissionEdge(PermissionResult permission) {
		return switch (permission) {
			case OWNER -> OWNER_EDGE;
			case TRUSTED -> TRUSTED_EDGE;
			case ADMIN_BYPASS -> ADMIN_EDGE;
			case DENIED -> BLOCKED_EDGE;
		};
	}

	private record PreviewContext(boolean useSurfaceAnchors, int referenceY) {
	}

	private record PreviewBlockPos(UUID worldId, int x, int y, int z) {
	}

	private record ConfirmedClaim(ClaimData claim, long expiresAt) {
	}
}
