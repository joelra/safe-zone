package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.GameplayConfig;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public final class PaperClaimWandState {
	private final Map<UUID, PendingSelection> firstCorners = new ConcurrentHashMap<>();
	private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<>();
	private final Map<UUID, ResizeState> resizeStates = new ConcurrentHashMap<>();

	public Optional<PendingSelection> getFirstCorner(UUID playerId) {
		return Optional.ofNullable(this.firstCorners.get(playerId));
	}

	public void setFirstCorner(UUID playerId, UUID worldId, ClaimCoordinates corner) {
		this.firstCorners.put(playerId, new PendingSelection(worldId, corner));
	}

	public boolean clearPendingSelection(UUID playerId) {
		return this.firstCorners.remove(playerId) != null;
	}

	public Optional<ResizeState> getResizeState(UUID playerId) {
		return Optional.ofNullable(this.resizeStates.get(playerId));
	}

	public void setResizeState(UUID playerId, UUID worldId, String claimId, ClaimCoordinates fixedCorner) {
		this.resizeStates.put(playerId, new ResizeState(worldId, claimId, fixedCorner));
	}

	public boolean clearPendingResize(UUID playerId) {
		return this.resizeStates.remove(playerId) != null;
	}

	public void setPendingRemoval(UUID playerId, String claimId, long expiresAt) {
		this.pendingRemovals.put(playerId, new PendingRemoval(claimId, expiresAt));
	}

	public void clearPendingRemoval(UUID playerId) {
		this.pendingRemovals.remove(playerId);
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

	public boolean hasPendingState(UUID playerId) {
		return this.firstCorners.containsKey(playerId)
			|| this.pendingRemovals.containsKey(playerId)
			|| this.resizeStates.containsKey(playerId);
	}

	public void clearPlayer(UUID playerId) {
		this.firstCorners.remove(playerId);
		this.pendingRemovals.remove(playerId);
		this.resizeStates.remove(playerId);
	}

	public boolean cancelIfNoLongerHoldingWand(Player player, GameplayConfig gameplayConfig) {
		UUID playerId = player.getUniqueId();
		boolean clearedSelection = this.firstCorners.remove(playerId) != null;
		boolean clearedResize = this.resizeStates.remove(playerId) != null;
		boolean clearedRemoval = this.pendingRemovals.remove(playerId) != null;
		if (!clearedSelection && !clearedResize && !clearedRemoval) {
			return false;
		}

		player.sendMessage(text(
			"Claim action cancelled because you put the " + PaperClaimWandSupport.claimWandName(gameplayConfig) + " away.",
			GRAY));
		return true;
	}

	public record PendingSelection(UUID worldId, ClaimCoordinates corner) {
	}

	private record PendingRemoval(String claimId, long expiresAt) {
		private boolean matches(String claimId, long now) {
			return this.claimId.equals(claimId) && now <= this.expiresAt;
		}
	}

	public record ResizeState(UUID worldId, String claimId, ClaimCoordinates fixedCorner) {
	}
}
