package com.simpleforapanda.safezone.item;

import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.manager.ClaimCreationResult;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.ClaimValidationFailure;
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
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimWandHandler {
	private final ClaimManager claimManager;
	private final Map<UUID, BlockPos> firstCorners = new ConcurrentHashMap<>();
	private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<>();
	private final Map<UUID, ResizeState> resizeStates = new ConcurrentHashMap<>();

	public ClaimWandHandler(ClaimManager claimManager) {
		this.claimManager = claimManager;
	}

	public InteractionResult handleUseOn(ServerPlayer player, ServerLevel level, BlockPos clickedPos) {
		if (player.isShiftKeyDown()) {
			if (clearPendingResize(player)) {
				playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55F, 0.8F);
				return InteractionResult.SUCCESS;
			}

			if (tryHandleRemoval(player, level, clickedPos)) {
				return InteractionResult.SUCCESS;
			}

			if (clearPendingSelection(player)) {
				playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55F, 0.8F);
				return InteractionResult.SUCCESS;
			}
		}

		if (!hasWandAccess(player, clickedPos)) {
			PlayerMessageHelper.sendError(player, SafeZoneText.wandForeignClaimDenied());
			return InteractionResult.FAIL;
		}

		ResizeState resizeState = this.resizeStates.get(player.getUUID());
		if (resizeState != null) {
			ClaimCreationResult result = this.claimManager.resizeClaim(player, resizeState.claimId(), resizeState.fixedCorner(), clickedPos);
			if (!result.created()) {
				sendValidationFailure(player, result.failure(), result.conflictingClaim());
				return InteractionResult.FAIL;
			}

			this.resizeStates.remove(player.getUUID());
			this.pendingRemovals.remove(player.getUUID());
			spawnCornerParticles(level, clickedPos, false);
			playFeedback(level, clickedPos, SoundEvents.PLAYER_LEVELUP, 0.75F, 1.0F);
			PlayerMessageHelper.sendStatus(player, "RESIZED", net.minecraft.ChatFormatting.GREEN,
				SafeZoneText.wandResized(formatClaimSummary(result.claim())));
			return InteractionResult.SUCCESS;
		}

		var clickedClaim = this.claimManager.getClaimAt(clickedPos);
		ClaimCoordinates clickedCoordinates = new ClaimCoordinates(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ());
		if (clickedClaim.isPresent() && clickedClaim.get().owns(player.getUUID()) && clickedClaim.get().isCorner(clickedCoordinates)) {
			ClaimCoordinates oppositeCorner = clickedClaim.get().getOppositeCorner(clickedCoordinates);
			this.resizeStates.put(player.getUUID(),
				new ResizeState(clickedClaim.get().claimId, new BlockPos(oppositeCorner.x(), oppositeCorner.y(), oppositeCorner.z())));
			this.firstCorners.remove(player.getUUID());
			this.pendingRemovals.remove(player.getUUID());
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.05F);
			TitleMessageHelper.showHint(player, SafeZoneText.RESIZE_READY_TITLE, SafeZoneText.RESIZE_READY_SUBTITLE);
			PlayerMessageHelper.sendStatus(player, "RESIZE", net.minecraft.ChatFormatting.AQUA,
				SafeZoneText.wandResizePickedUp(formatClaimSummary(clickedClaim.get())));
			PlayerMessageHelper.sendStep(player, SafeZoneText.wandResizeStep(wandName()));
			return InteractionResult.SUCCESS;
		}

		BlockPos firstCorner = this.firstCorners.get(player.getUUID());
		if (firstCorner == null) {
			this.firstCorners.put(player.getUUID(), clickedPos.immutable());
			this.pendingRemovals.remove(player.getUUID());
			spawnCornerParticles(level, clickedPos, true);
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
			TitleMessageHelper.showHint(player, SafeZoneText.CORNER_SAVED_TITLE, SafeZoneText.CORNER_SAVED_SUBTITLE);
			PlayerMessageHelper.sendStatus(player, "CORNER 1", net.minecraft.ChatFormatting.GREEN,
				SafeZoneText.wandCornerSaved());
			PlayerMessageHelper.sendStep(player, SafeZoneText.wandCornerStep(wandName()));
			return InteractionResult.SUCCESS;
		}

		ClaimCreationResult result = this.claimManager.createClaim(player, firstCorner, clickedPos);
		if (!result.created()) {
			sendValidationFailure(player, result.failure(), result.conflictingClaim());
			return InteractionResult.FAIL;
		}

		this.firstCorners.remove(player.getUUID());
		this.pendingRemovals.remove(player.getUUID());
		spawnCornerParticles(level, clickedPos, false);
		playFeedback(level, clickedPos, SoundEvents.PLAYER_LEVELUP, 0.75F, 1.0F);
		PlayerMessageHelper.sendStatus(player, "CLAIMED", net.minecraft.ChatFormatting.GREEN,
			SafeZoneText.wandClaimed(formatClaimSummary(result.claim())));
		PlayerMessageHelper.sendStep(player, SafeZoneText.wandShareStep(wandName()));
		return InteractionResult.SUCCESS;
	}

	private boolean tryHandleRemoval(ServerPlayer player, ServerLevel level, BlockPos clickedPos) {
		var claim = this.claimManager.getClaimAt(clickedPos);
		if (claim.isEmpty() || !claim.get().owns(player.getUUID())) {
			// Fallback: if the click landed outside the claim (e.g. extended wand reach while
			// looking horizontally), check whether the player is standing inside their own claim.
			claim = this.claimManager.getClaimAt(player.blockPosition());
		}
		if (claim.isEmpty() || !claim.get().owns(player.getUUID())) {
			return false;
		}

		String claimSummary = formatClaimSummary(claim.get());
		long now = System.currentTimeMillis();
		PendingRemoval pendingRemoval = this.pendingRemovals.get(player.getUUID());
		if (pendingRemoval != null && pendingRemoval.matches(claim.get().claimId, now)) {
			this.claimManager.removeClaim(claim.get().claimId);
			this.pendingRemovals.remove(player.getUUID());
			this.firstCorners.remove(player.getUUID());
			this.resizeStates.remove(player.getUUID());
			level.sendParticles(ParticleTypes.POOF, clickedPos.getX() + 0.5D, clickedPos.getY() + 1.0D, clickedPos.getZ() + 0.5D, 12, 0.45D, 0.35D, 0.45D, 0.02D);
			playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9F, 0.65F);
			PlayerMessageHelper.sendStatus(player, "REMOVED", net.minecraft.ChatFormatting.RED,
				SafeZoneText.wandRemoved(claimSummary));
			return true;
		}

		GameplayConfig gameplayConfig = this.claimManager.getGameplayConfig();
		this.pendingRemovals.put(player.getUUID(), new PendingRemoval(claim.get().claimId, now + gameplayConfig.wandRemoveConfirmWindowMillis()));
		TitleMessageHelper.showHint(player, SafeZoneText.REMOVE_AREA_TITLE, SafeZoneText.REMOVE_AREA_SUBTITLE);
		PlayerMessageHelper.sendWarning(player, SafeZoneText.wandRemovePrompt(claimSummary));
		PlayerMessageHelper.sendStep(player,
			SafeZoneText.wandRemoveConfirmStep(gameplayConfig.wandRemoveConfirmSeconds));
		playFeedback(level, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 0.5F);
		return true;
	}

	private boolean clearPendingSelection(ServerPlayer player) {
		BlockPos removed = this.firstCorners.remove(player.getUUID());
		if (removed == null) {
			return false;
		}

		this.pendingRemovals.remove(player.getUUID());
		PlayerMessageHelper.sendInfo(player, SafeZoneText.CLAIM_ACTION_CANCELLED);
		PlayerMessageHelper.sendStep(player, SafeZoneText.CLAIM_STEP_RESTART);
		return true;
	}

	private boolean clearPendingResize(ServerPlayer player) {
		ResizeState removed = this.resizeStates.remove(player.getUUID());
		if (removed == null) {
			return false;
		}

		this.pendingRemovals.remove(player.getUUID());
		PlayerMessageHelper.sendInfo(player, SafeZoneText.CLAIM_RESIZE_CANCELLED);
		PlayerMessageHelper.sendStep(player, SafeZoneText.CLAIM_STEP_RETRY_RESIZE);
		return true;
	}

	private boolean hasWandAccess(ServerPlayer player, BlockPos pos) {
		var claim = this.claimManager.getClaimAt(pos);
		return claim.isEmpty()
			|| claim.get().owns(player.getUUID())
			|| claim.get().isTrusted(player.getUUID())
			|| player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
	}

	private void sendValidationFailure(ServerPlayer player, ClaimValidationFailure failure, com.simpleforapanda.safezone.data.ClaimData conflictingClaim) {
		GameplayConfig gameplayConfig = this.claimManager.getGameplayConfig();
		String message = switch (failure) {
			case DIMENSION_NOT_ALLOWED -> SafeZoneText.validationOverworldSentence();
			case CLAIM_LIMIT_REACHED -> SafeZoneText.validationClaimLimitSentence(this.claimManager.getMaxClaims(player.getUUID()));
			case CLAIM_TOO_LARGE -> SafeZoneText.validationTooBigSentence(gameplayConfig.maxClaimWidth, gameplayConfig.maxClaimDepth);
			case TOO_CLOSE_TO_EXISTING_CLAIM -> conflictingClaim == null
				? SafeZoneText.validationTooCloseSentence(gameplayConfig.effectiveMinDistance())
				: SafeZoneText.validationTooCloseSentence(conflictingClaim.ownerName, conflictingClaim.claimId);
		};

		PlayerMessageHelper.sendError(player, message);
	}

	public Optional<BlockPos> getFirstCorner(UUID playerId) {
		return Optional.ofNullable(this.firstCorners.get(playerId));
	}

	public boolean hasPendingSelection(UUID playerId) {
		return this.firstCorners.containsKey(playerId);
	}

	public boolean clearPlayer(UUID playerId) {
		boolean clearedSelection = this.firstCorners.remove(playerId) != null;
		boolean clearedResize = this.resizeStates.remove(playerId) != null;
		boolean clearedRemoval = this.pendingRemovals.remove(playerId) != null;
		return clearedSelection || clearedResize || clearedRemoval;
	}

	public Optional<ResizeState> getResizeState(UUID playerId) {
		return Optional.ofNullable(this.resizeStates.get(playerId));
	}

	public boolean cancelIfNoLongerHoldingWand(ServerPlayer player) {
		boolean clearedSelection = this.firstCorners.remove(player.getUUID()) != null;
		boolean clearedResize = this.resizeStates.remove(player.getUUID()) != null;
		boolean clearedRemoval = this.pendingRemovals.remove(player.getUUID()) != null;
		if (!clearedSelection && !clearedResize && !clearedRemoval) {
			return false;
		}

		PlayerMessageHelper.sendInfo(player, SafeZoneText.wandPutAwayCancelled(wandName()));
		return true;
	}

	public boolean isPendingRemoval(UUID playerId, String claimId) {
		PendingRemoval pendingRemoval = this.pendingRemovals.get(playerId);
		if (pendingRemoval == null) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (!pendingRemoval.matches(claimId, now)) {
			if (now > pendingRemoval.expiresAt()) {
				this.pendingRemovals.remove(playerId, pendingRemoval);
			}
			return false;
		}

		return true;
	}

	public boolean isClaimWand(ItemStack stack) {
		return ModItems.isClaimWand(stack, this.claimManager.getGameplayConfig());
	}

	public ItemStack createClaimWandStack() {
		return ModItems.createClaimWandStack(this.claimManager.getGameplayConfig());
	}

	public String wandName() {
		return ModItems.claimWandName(this.claimManager.getGameplayConfig());
	}

	private static String formatClaimSummary(com.simpleforapanda.safezone.data.ClaimData claim) {
		return claim.claimId + " (" + claim.getWidth() + "x" + claim.getDepth() + ")";
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
