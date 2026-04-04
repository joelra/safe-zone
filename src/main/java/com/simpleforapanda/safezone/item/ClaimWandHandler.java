package com.simpleforapanda.safezone.item;

import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.manager.TitleMessageHelper;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

	public final class ClaimWandHandler {
		private static final Map<UUID, BlockPos> FIRST_CORNERS = new ConcurrentHashMap<>();
		private static final Map<UUID, PendingRemoval> PENDING_REMOVALS = new ConcurrentHashMap<>();
		private static final Map<UUID, ResizeState> RESIZE_STATES = new ConcurrentHashMap<>();

	private ClaimWandHandler() {
	}

		public static InteractionResult handleUseOn(ServerPlayer player, ServerLevel level, BlockPos clickedPos) {
			ClaimManager claimManager = ClaimManager.getInstance();

			if (player.isShiftKeyDown()) {
				if (clearPendingResize(player)) {
					playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55F, 0.8F);
					return InteractionResult.SUCCESS;
				}

				if (tryHandleRemoval(player, level, clickedPos, claimManager)) {
					return InteractionResult.SUCCESS;
				}

			if (clearPendingSelection(player)) {
				playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55F, 0.8F);
				return InteractionResult.SUCCESS;
			}
		}

		if (!hasWandAccess(player, clickedPos, claimManager)) {
			PlayerMessageHelper.sendError(player, SafeZoneText.wandForeignClaimDenied());
			return InteractionResult.FAIL;
		}

		ResizeState resizeState = RESIZE_STATES.get(player.getUUID());
		if (resizeState != null) {
			ClaimManager.ClaimCreationResult result = claimManager.resizeClaim(player, resizeState.claimId(), resizeState.fixedCorner(), clickedPos);
			if (!result.created()) {
				sendValidationFailure(player, result.failure(), result.conflictingClaim());
				return InteractionResult.FAIL;
			}

			RESIZE_STATES.remove(player.getUUID());
			PENDING_REMOVALS.remove(player.getUUID());
			spawnCornerParticles(level, clickedPos, false);
			playFeedback(level, clickedPos, SoundEvents.PLAYER_LEVELUP, 0.75F, 1.0F);
			PlayerMessageHelper.sendStatus(player, "RESIZED", net.minecraft.ChatFormatting.GREEN,
				SafeZoneText.wandResized(formatClaimSummary(result.claim())));
			return InteractionResult.SUCCESS;
		}

		var clickedClaim = claimManager.getClaimAt(clickedPos);
		if (clickedClaim.isPresent() && clickedClaim.get().owns(player.getUUID()) && clickedClaim.get().isCorner(clickedPos)) {
			ClaimWandHandler.RESIZE_STATES.put(player.getUUID(), new ResizeState(clickedClaim.get().claimId, clickedClaim.get().getOppositeCorner(clickedPos)));
			FIRST_CORNERS.remove(player.getUUID());
			PENDING_REMOVALS.remove(player.getUUID());
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.05F);
			TitleMessageHelper.showHint(player, SafeZoneText.RESIZE_READY_TITLE, SafeZoneText.RESIZE_READY_SUBTITLE);
			PlayerMessageHelper.sendStatus(player, "RESIZE", net.minecraft.ChatFormatting.AQUA,
				SafeZoneText.wandResizePickedUp(formatClaimSummary(clickedClaim.get())));
			PlayerMessageHelper.sendStep(player, SafeZoneText.wandResizeStep(wandName()));
			return InteractionResult.SUCCESS;
		}

		BlockPos firstCorner = FIRST_CORNERS.get(player.getUUID());
		if (firstCorner == null) {
			FIRST_CORNERS.put(player.getUUID(), clickedPos.immutable());
			PENDING_REMOVALS.remove(player.getUUID());
			spawnCornerParticles(level, clickedPos, true);
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
			TitleMessageHelper.showHint(player, SafeZoneText.CORNER_SAVED_TITLE, SafeZoneText.CORNER_SAVED_SUBTITLE);
			PlayerMessageHelper.sendStatus(player, "CORNER 1", net.minecraft.ChatFormatting.GREEN,
				SafeZoneText.wandCornerSaved());
			PlayerMessageHelper.sendStep(player, SafeZoneText.wandCornerStep(wandName()));
			return InteractionResult.SUCCESS;
		}

		ClaimManager.ClaimCreationResult result = claimManager.createClaim(player, firstCorner, clickedPos);
		if (!result.created()) {
			sendValidationFailure(player, result.failure(), result.conflictingClaim());
			return InteractionResult.FAIL;
		}

		FIRST_CORNERS.remove(player.getUUID());
		PENDING_REMOVALS.remove(player.getUUID());
		spawnCornerParticles(level, clickedPos, false);
		playFeedback(level, clickedPos, SoundEvents.PLAYER_LEVELUP, 0.75F, 1.0F);
		PlayerMessageHelper.sendStatus(player, "CLAIMED", net.minecraft.ChatFormatting.GREEN,
			SafeZoneText.wandClaimed(formatClaimSummary(result.claim())));
		PlayerMessageHelper.sendStep(player, SafeZoneText.wandShareStep(wandName()));
		return InteractionResult.SUCCESS;
	}

	private static boolean tryHandleRemoval(ServerPlayer player, ServerLevel level, BlockPos clickedPos, ClaimManager claimManager) {
		var claim = claimManager.getClaimAt(clickedPos);
		if (claim.isEmpty() || !claim.get().owns(player.getUUID())) {
			return false;
		}

		String claimSummary = formatClaimSummary(claim.get());
		long now = System.currentTimeMillis();
		PendingRemoval pendingRemoval = PENDING_REMOVALS.get(player.getUUID());
		if (pendingRemoval != null && pendingRemoval.matches(claim.get().claimId, now)) {
			claimManager.removeClaim(claim.get().claimId);
			PENDING_REMOVALS.remove(player.getUUID());
			FIRST_CORNERS.remove(player.getUUID());
			level.sendParticles(ParticleTypes.POOF, clickedPos.getX() + 0.5D, clickedPos.getY() + 1.0D, clickedPos.getZ() + 0.5D, 12, 0.45D, 0.35D, 0.45D, 0.02D);
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9F, 0.65F);
			PlayerMessageHelper.sendStatus(player, "REMOVED", net.minecraft.ChatFormatting.RED,
				SafeZoneText.wandRemoved(claimSummary));
			return true;
		}

		GameplayConfig gameplayConfig = claimManager.getGameplayConfig();
		PENDING_REMOVALS.put(player.getUUID(), new PendingRemoval(claim.get().claimId, now + gameplayConfig.wandRemoveConfirmWindowMillis()));
		TitleMessageHelper.showHint(player, SafeZoneText.REMOVE_AREA_TITLE, SafeZoneText.REMOVE_AREA_SUBTITLE);
		PlayerMessageHelper.sendWarning(player, SafeZoneText.wandRemovePrompt(claimSummary));
		PlayerMessageHelper.sendStep(player,
			SafeZoneText.wandRemoveConfirmStep(gameplayConfig.wandRemoveConfirmSeconds));
		playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 0.5F);
		return true;
	}

	private static boolean clearPendingSelection(ServerPlayer player) {
		BlockPos removed = FIRST_CORNERS.remove(player.getUUID());
		if (removed == null) {
			return false;
		}

		PENDING_REMOVALS.remove(player.getUUID());
		PlayerMessageHelper.sendInfo(player, SafeZoneText.CLAIM_ACTION_CANCELLED);
		PlayerMessageHelper.sendStep(player, SafeZoneText.CLAIM_STEP_RESTART);
		return true;
	}

	private static boolean clearPendingResize(ServerPlayer player) {
		ResizeState removed = RESIZE_STATES.remove(player.getUUID());
		if (removed == null) {
			return false;
		}

		PENDING_REMOVALS.remove(player.getUUID());
		PlayerMessageHelper.sendInfo(player, SafeZoneText.CLAIM_RESIZE_CANCELLED);
		PlayerMessageHelper.sendStep(player, SafeZoneText.CLAIM_STEP_RETRY_RESIZE);
		return true;
	}

	private static boolean hasWandAccess(ServerPlayer player, BlockPos pos, ClaimManager claimManager) {
		var claim = claimManager.getClaimAt(pos);
		return claim.isEmpty()
			|| claim.get().owns(player.getUUID())
			|| claim.get().isTrusted(player.getUUID())
			|| player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
	}

	private static void sendValidationFailure(ServerPlayer player, ClaimManager.ClaimValidationFailure failure, com.simpleforapanda.safezone.data.ClaimData conflictingClaim) {
		ClaimManager claimManager = ClaimManager.getInstance();
		GameplayConfig gameplayConfig = claimManager.getGameplayConfig();
		String message = switch (failure) {
			case DIMENSION_NOT_ALLOWED -> SafeZoneText.validationOverworldSentence();
			case CLAIM_LIMIT_REACHED -> SafeZoneText.validationClaimLimitSentence(claimManager.getMaxClaims(player.getUUID()));
			case CLAIM_TOO_LARGE -> SafeZoneText.validationTooBigSentence(gameplayConfig.maxClaimWidth, gameplayConfig.maxClaimDepth);
			case TOO_CLOSE_TO_EXISTING_CLAIM -> conflictingClaim == null
				? SafeZoneText.validationTooCloseSentence(gameplayConfig.effectiveMinDistance())
				: SafeZoneText.validationTooCloseSentence(conflictingClaim.ownerName, conflictingClaim.claimId);
		};

		PlayerMessageHelper.sendError(player, message);
	}

	public static Optional<BlockPos> getFirstCorner(UUID playerId) {
		return Optional.ofNullable(FIRST_CORNERS.get(playerId));
	}

	public static boolean hasPendingSelection(UUID playerId) {
		return FIRST_CORNERS.containsKey(playerId);
	}

	public static Optional<ResizeState> getResizeState(UUID playerId) {
		return Optional.ofNullable(RESIZE_STATES.get(playerId));
	}

	public static boolean cancelIfNoLongerHoldingWand(ServerPlayer player) {
		boolean clearedSelection = FIRST_CORNERS.remove(player.getUUID()) != null;
		boolean clearedResize = RESIZE_STATES.remove(player.getUUID()) != null;
		boolean clearedRemoval = PENDING_REMOVALS.remove(player.getUUID()) != null;
		if (!clearedSelection && !clearedResize && !clearedRemoval) {
			return false;
		}

		PlayerMessageHelper.sendInfo(player, SafeZoneText.wandPutAwayCancelled(wandName()));
		return true;
	}

	public static boolean isPendingRemoval(UUID playerId, String claimId) {
		PendingRemoval pendingRemoval = PENDING_REMOVALS.get(playerId);
		if (pendingRemoval == null) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (!pendingRemoval.matches(claimId, now)) {
			if (now > pendingRemoval.expiresAt()) {
				PENDING_REMOVALS.remove(playerId, pendingRemoval);
			}
			return false;
		}

		return true;
	}

	private static String formatClaimSummary(com.simpleforapanda.safezone.data.ClaimData claim) {
		return claim.claimId + " (" + claim.getWidth() + "x" + claim.getDepth() + ")";
	}

	private static String wandName() {
		return ModItems.claimWandName();
	}

	private static void spawnCornerParticles(ServerLevel level, BlockPos pos, boolean firstCorner) {
		level.sendParticles(firstCorner ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ENCHANT,
			pos.getX() + 0.5D,
			pos.getY() + 1.0D,
			pos.getZ() + 0.5D,
			firstCorner ? 10 : 16,
			0.45D,
			0.35D,
			0.45D,
			0.02D);
	}

	private static void playFeedback(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private record PendingRemoval(String claimId, long expiresAt) {
		private boolean matches(String claimId, long now) {
			return this.claimId.equals(claimId) && now <= this.expiresAt;
		}
	}

	public record ResizeState(String claimId, BlockPos fixedCorner) {
	}
}
