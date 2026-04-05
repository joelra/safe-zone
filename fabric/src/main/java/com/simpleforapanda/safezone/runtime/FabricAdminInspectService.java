package com.simpleforapanda.safezone.runtime;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricAdminInspectService {
	private final Set<UUID> inspectingPlayers = ConcurrentHashMap.newKeySet();

	public boolean toggle(UUID playerId) {
		if (this.inspectingPlayers.remove(playerId)) {
			return false;
		}

		this.inspectingPlayers.add(playerId);
		return true;
	}

	public boolean isInspecting(UUID playerId) {
		return this.inspectingPlayers.contains(playerId);
	}

	public void clear() {
		this.inspectingPlayers.clear();
	}

	public boolean tryInspectClaim(ClaimManager claimManager, ServerPlayer player, ItemStack heldItem, BlockPos pos) {
		if (!isInspecting(player.getUUID())) {
			return false;
		}
		if (!player.isShiftKeyDown() || !heldItem.isEmpty()) {
			return false;
		}

		var claim = claimManager.getClaimAt(pos);
		if (claim.isEmpty()) {
			PlayerMessageHelper.sendInfo(player, SafeZoneText.NO_SAFE_ZONE_AT_SPOT);
			return true;
		}

		ClaimData claimData = claim.get();
		PlayerMessageHelper.sendStatus(player, "INSPECT", ChatFormatting.AQUA,
			SafeZoneText.adminInspectSummary(claimData.claimId, claimData.ownerName, claimData.getWidth(), claimData.getDepth(), claimData.trusted.size()));
		return true;
	}
}
