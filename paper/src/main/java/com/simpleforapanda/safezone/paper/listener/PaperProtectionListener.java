package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperProtectionListener implements Listener {
	private static final String ADMIN_PERMISSION = "safezone.command.admin";
	private static final long BLOCKED_MESSAGE_COOLDOWN_MILLIS = 250L;
	private static final Map<UUID, Long> LAST_BLOCKED_MESSAGE_AT = new ConcurrentHashMap<>();

	private final PaperRuntime runtime;

	public PaperProtectionListener(PaperRuntime runtime) {
		this.runtime = runtime;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!isDenied(event.getPlayer(), event.getBlock().getLocation())) {
			return;
		}

		event.setCancelled(true);
		sendBlockedMessage(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (hasDeniedPlacement(event)) {
			event.setCancelled(true);
			denyItemUse(event.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
			return;
		}

		Block clickedBlock = event.getClickedBlock();
		if (clickedBlock == null) {
			return;
		}

		if (PaperClaimWandSupport.isClaimWand(event.getItem(), this.runtime.services().configService().current().gameplay)) {
			return;
		}

		if (usesPlacementTarget(event.getMaterial())) {
			return;
		}

		if (!isDenied(event.getPlayer(), clickedBlock.getLocation())) {
			return;
		}

		cancelInteraction(event);
		denyItemUse(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
		if (!isDenied(event.getPlayer(), event.getBlock().getLocation())) {
			return;
		}

		event.setCancelled(true);
		denyItemUse(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerBucketFill(PlayerBucketFillEvent event) {
		if (!isDenied(event.getPlayer(), event.getBlockClicked().getLocation())) {
			return;
		}

		event.setCancelled(true);
		denyItemUse(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event) {
		if (!event.getBlock().isLiquid()) {
			return;
		}

		Optional<ClaimData> targetClaim = claimStore().getClaimAt(event.getToBlock().getLocation());
		if (targetClaim.isEmpty()) {
			return;
		}

		Optional<ClaimData> sourceClaim = claimStore().getClaimAt(event.getBlock().getLocation());
		if (sourceClaim.isPresent() && sourceClaim.get().claimId.equals(targetClaim.get().claimId)) {
			return;
		}

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockIgnite(BlockIgniteEvent event) {
		if (!switch (event.getCause()) {
			case SPREAD, LAVA, EXPLOSION -> true;
			default -> false;
		}) {
			return;
		}

		if (claimStore().getClaimAt(event.getBlock().getLocation()).isPresent()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockSpread(BlockSpreadEvent event) {
		Material sourceType = event.getSource().getType();
		if (sourceType != Material.FIRE && sourceType != Material.SOUL_FIRE) {
			return;
		}

		if (claimStore().getClaimAt(event.getBlock().getLocation()).isPresent()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {
		if (claimStore().getClaimAt(event.getBlock().getLocation()).isPresent()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(block -> claimStore().getClaimAt(block.getLocation()).isPresent());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(block -> claimStore().getClaimAt(block.getLocation()).isPresent());
	}

	private boolean hasDeniedPlacement(BlockPlaceEvent event) {
		if (event instanceof BlockMultiPlaceEvent multiPlaceEvent) {
			for (BlockState blockState : multiPlaceEvent.getReplacedBlockStates()) {
				if (isDenied(event.getPlayer(), blockState.getLocation())) {
					return true;
				}
			}
			return false;
		}

		return isDenied(event.getPlayer(), event.getBlockPlaced().getLocation());
	}

	private boolean isDenied(Player player, Location location) {
		Optional<ClaimData> claim = claimStore().getClaimAt(location);
		if (claim.isEmpty()) {
			return false;
		}

		return claimStore().getPermission(claim.get(), player.getUniqueId(), player.hasPermission(ADMIN_PERMISSION)) == PermissionResult.DENIED;
	}

	private PaperClaimStore claimStore() {
		return this.runtime.services().claimStore();
	}

	private static void cancelInteraction(PlayerInteractEvent event) {
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);
		event.setCancelled(true);
	}

	private static boolean isBucket(Material material) {
		return material == Material.BUCKET || material.name().endsWith("_BUCKET");
	}

	private static boolean usesPlacementTarget(Material material) {
		return material.isBlock() || isBucket(material);
	}

	public static void sendBlockedMessage(Player player) {
		long now = System.currentTimeMillis();
		Long lastSentAt = LAST_BLOCKED_MESSAGE_AT.get(player.getUniqueId());
		if (lastSentAt != null && now - lastSentAt < BLOCKED_MESSAGE_COOLDOWN_MILLIS) {
			return;
		}

		LAST_BLOCKED_MESSAGE_AT.put(player.getUniqueId(), now);
		player.sendMessage(text("You cannot build here yet. Ask the owner to trust you first.", YELLOW));
	}

	private static void denyItemUse(Player player) {
		sendBlockedMessage(player);
		player.updateInventory();
	}
}
