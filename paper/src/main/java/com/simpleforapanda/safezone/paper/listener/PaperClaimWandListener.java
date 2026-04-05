package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.ClaimCreationResult;
import com.simpleforapanda.safezone.manager.ClaimValidationFailure;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import com.simpleforapanda.safezone.text.SafeZoneText;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public final class PaperClaimWandListener implements Listener {
	private final PaperRuntime runtime;
	private final PaperClaimWandState wandState;

	public PaperClaimWandListener(PaperRuntime runtime, PaperClaimWandState wandState) {
		this.runtime = runtime;
		this.wandState = wandState;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}

		Block clickedBlock = event.getClickedBlock();
		if (clickedBlock == null) {
			return;
		}

		SafeZoneConfig config = this.runtime.services().configService().current();
		if (!PaperClaimWandSupport.isClaimWand(event.getItem(), config.gameplay)) {
			return;
		}

		Player player = event.getPlayer();
		Action action = event.getAction();
		if (action == Action.LEFT_CLICK_BLOCK) {
			if (hasPendingState(player)) {
				event.setUseInteractedBlock(Event.Result.DENY);
				event.setUseItemInHand(Event.Result.DENY);
				event.setCancelled(true);
				return;
			}
			if (player.isSneaking()) {
				event.setUseInteractedBlock(Event.Result.DENY);
				event.setUseItemInHand(Event.Result.DENY);
				event.setCancelled(true);
				tryOpenTrustMenu(player, clickedBlock, this.runtime.services().claimStore());
			}
			return;
		}
		if (action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);
		event.setCancelled(true);

		PaperClaimStore claimStore = this.runtime.services().claimStore();
		if (!claimStore.isClaimWorld(clickedBlock.getWorld())) {
			player.sendMessage(text("Claims can only be created in the main Overworld.", RED));
			return;
		}

		if (player.isSneaking()) {
			if (clearPendingResize(player)) {
				return;
			}
			if (tryHandleRemoval(player, clickedBlock, claimStore, config.gameplay)) {
				return;
			}
			if (clearPendingSelection(player)) {
				return;
			}
		}

		Optional<ClaimData> clickedClaim = claimStore.getClaimAt(clickedBlock.getLocation());
		if (clickedClaim.isPresent()) {
			PermissionResult access = claimStore.getPermission(clickedClaim.get(), player.getUniqueId(), player.hasPermission("safezone.command.admin"));
			if (access == PermissionResult.DENIED) {
				player.sendMessage(text("You can only use the claim wand in unclaimed land or inside claims you can access.", RED));
				return;
			}
		}

		ClaimCoordinates clickedCorner = toClaimCoordinates(clickedBlock);
		PaperClaimWandState.ResizeState resizeState = this.wandState.getResizeState(player.getUniqueId()).orElse(null);
		if (resizeState != null) {
			if (!resizeState.worldId().equals(clickedBlock.getWorld().getUID())) {
				player.sendMessage(text("Finish resizing in the same world where you started.", RED));
				return;
			}

			ClaimCreationResult result = claimStore.resizeClaim(
				clickedBlock.getWorld(),
				player.getUniqueId(),
				resizeState.claimId(),
				resizeState.fixedCorner(),
				clickedCorner);
			if (!result.created()) {
				player.sendMessage(text(validationFailureMessage(result.failure(), result.conflictingClaim(), config, claimStore, player), RED));
				return;
			}

			this.wandState.clearPendingResize(player.getUniqueId());
			this.wandState.clearPendingRemoval(player.getUniqueId());
			player.sendMessage(text("Updated claim " + formatClaimSummary(result.claim()) + ".", GREEN));
			player.sendMessage(text("Use /claim here to inspect the new bounds.", AQUA));
			return;
		}

		if (clickedClaim.isPresent() && clickedClaim.get().owns(player.getUniqueId()) && clickedClaim.get().isCorner(clickedCorner)) {
			ClaimCoordinates oppositeCorner = clickedClaim.get().getOppositeCorner(clickedCorner);
			this.wandState.setResizeState(player.getUniqueId(), clickedBlock.getWorld().getUID(), clickedClaim.get().claimId, oppositeCorner);
			this.wandState.clearPendingSelection(player.getUniqueId());
			this.wandState.clearPendingRemoval(player.getUniqueId());
			PaperTitleMessageHelper.showHint(player, SafeZoneText.RESIZE_READY_TITLE, SafeZoneText.RESIZE_READY_SUBTITLE);
			player.sendMessage(text("Picked up " + formatClaimSummary(clickedClaim.get()) + " for resizing.", AQUA));
			player.sendMessage(text("Right-click the new corner with your claim wand.", GRAY));
			return;
		}

		PaperClaimWandState.PendingSelection firstSelection = this.wandState.getFirstCorner(player.getUniqueId()).orElse(null);
		if (firstSelection == null) {
			this.wandState.setFirstCorner(player.getUniqueId(), clickedBlock.getWorld().getUID(), clickedCorner);
			this.wandState.clearPendingRemoval(player.getUniqueId());
			PaperTitleMessageHelper.showHint(player, SafeZoneText.CORNER_SAVED_TITLE, SafeZoneText.CORNER_SAVED_SUBTITLE);
			player.sendMessage(text("Saved corner 1 at " + formatCorner(clickedCorner) + ".", GREEN));
			player.sendMessage(text("Right-click the opposite corner with your claim wand.", GRAY));
			return;
		}

		if (!firstSelection.worldId().equals(clickedBlock.getWorld().getUID())) {
			player.sendMessage(text("Finish your claim in the same world where you saved corner 1.", RED));
			return;
		}

		ClaimCreationResult result = claimStore.createClaim(
			clickedBlock.getWorld(),
			player.getUniqueId(),
			player.getName(),
			firstSelection.corner(),
			clickedCorner);
		if (!result.created()) {
			player.sendMessage(text(validationFailureMessage(result.failure(), result.conflictingClaim(), config, claimStore, player), RED));
			return;
		}

		this.wandState.clearPendingSelection(player.getUniqueId());
		this.wandState.clearPendingRemoval(player.getUniqueId());
		player.sendMessage(text("Created claim " + formatClaimSummary(result.claim()) + ".", GREEN));
		player.sendMessage(text("Use /claim trust to manage build access for this claim.", AQUA));
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		this.wandState.clearPlayer(event.getPlayer().getUniqueId());
	}

	private boolean tryHandleRemoval(Player player, Block clickedBlock, PaperClaimStore claimStore, GameplayConfig gameplayConfig) {
		Optional<ClaimData> clickedClaim = claimStore.getClaimAt(clickedBlock.getLocation());
		if (clickedClaim.isEmpty() || !clickedClaim.get().owns(player.getUniqueId())) {
			return false;
		}

		ClaimData claim = clickedClaim.get();
		long now = System.currentTimeMillis();
		if (this.wandState.isPendingRemoval(player.getUniqueId(), claim.claimId)) {
			this.wandState.clearPendingRemoval(player.getUniqueId());
			this.wandState.clearPendingSelection(player.getUniqueId());
			this.wandState.clearPendingResize(player.getUniqueId());
			if (!claimStore.removeClaim(claim.claimId)) {
				player.sendMessage(text("That claim was already removed.", RED));
				return true;
			}
			player.sendMessage(text("Removed " + formatClaimSummary(claim) + ".", RED));
			return true;
		}

		this.wandState.setPendingRemoval(player.getUniqueId(), claim.claimId, now + gameplayConfig.wandRemoveConfirmWindowMillis());
		PaperTitleMessageHelper.showHint(player, SafeZoneText.REMOVE_AREA_TITLE, SafeZoneText.REMOVE_AREA_SUBTITLE);
		player.sendMessage(text("Remove " + formatClaimSummary(claim) + "?", RED));
		player.sendMessage(text(
			"Hold Shift and right-click the same safe zone again within " + gameplayConfig.wandRemoveConfirmSeconds + " seconds to confirm.",
			GRAY));
		return true;
	}

	private boolean clearPendingSelection(Player player) {
		if (!this.wandState.clearPendingSelection(player.getUniqueId())) {
			return false;
		}

		this.wandState.clearPendingRemoval(player.getUniqueId());
		player.sendMessage(text("Claim action cancelled.", RED));
		player.sendMessage(text("Right-click the first corner again to start over.", GRAY));
		return true;
	}

	private boolean clearPendingResize(Player player) {
		if (!this.wandState.clearPendingResize(player.getUniqueId())) {
			return false;
		}

		this.wandState.clearPendingRemoval(player.getUniqueId());
		player.sendMessage(text("Resize cancelled.", RED));
		player.sendMessage(text("Right-click a claim corner again if you want to resize it.", GRAY));
		return true;
	}

	private String validationFailureMessage(
		ClaimValidationFailure failure,
		ClaimData conflictingClaim,
		SafeZoneConfig config,
		PaperClaimStore claimStore,
		Player player
	) {
		return switch (failure) {
			case DIMENSION_NOT_ALLOWED -> "Claims can only be created in the Overworld.";
			case CLAIM_LIMIT_REACHED -> "You have reached your claim limit of "
				+ claimStore.getMaxClaims(player.getUniqueId(), config.gameplay.defaultMaxClaims) + ".";
			case CLAIM_TOO_LARGE -> "Claims can be at most "
				+ config.gameplay.maxClaimWidth + "x" + config.gameplay.maxClaimDepth + ".";
			case TOO_CLOSE_TO_EXISTING_CLAIM -> conflictingClaim == null
				? "That area is too close to another claim."
				: "That area is too close to " + conflictingClaim.ownerName + "'s claim " + conflictingClaim.claimId + ".";
		};
	}

	private static ClaimCoordinates toClaimCoordinates(Block clickedBlock) {
		return new ClaimCoordinates(clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());
	}

	private static String formatCorner(ClaimCoordinates corner) {
		return corner.x() + ", " + corner.z();
	}

	private static String formatClaimSummary(ClaimData claim) {
		return claim.claimId + " (" + claim.getWidth() + "x" + claim.getDepth() + ")";
	}

	private boolean hasPendingState(Player player) {
		return this.wandState.hasPendingState(player.getUniqueId());
	}

	private void tryOpenTrustMenu(Player player, Block clickedBlock, PaperClaimStore claimStore) {
		Optional<ClaimData> clickedClaim = claimStore.getClaimAt(clickedBlock.getLocation());
		if (clickedClaim.isEmpty()) {
			return;
		}

		PermissionResult permission = claimStore.getPermission(clickedClaim.get(), player.getUniqueId(), player.hasPermission("safezone.command.admin"));
		if (!clickedClaim.get().owns(player.getUniqueId()) && permission != PermissionResult.ADMIN_BYPASS) {
			player.sendMessage(text("Only the owner or an admin can change build access here.", RED));
			return;
		}

		this.runtime.services().trustMenuService().open(player, clickedClaim.get().claimId);
	}
}
