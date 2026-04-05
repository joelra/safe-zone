package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperAdminInspectService implements Listener {
	private static final String ADMIN_PERMISSION = "safezone.command.admin";

	private final PaperRuntime runtime;
	private final Set<UUID> inspectingPlayers = ConcurrentHashMap.newKeySet();

	public PaperAdminInspectService(PaperRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	public boolean toggle(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		if (this.inspectingPlayers.remove(playerId)) {
			return false;
		}

		this.inspectingPlayers.add(playerId);
		return true;
	}

	public void clear() {
		this.inspectingPlayers.clear();
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		if (event.getClickedBlock() == null) {
			return;
		}

		Player player = event.getPlayer();
		if (!player.isSneaking() || !player.hasPermission(ADMIN_PERMISSION) || !this.inspectingPlayers.contains(player.getUniqueId())) {
			return;
		}
		if (event.getItem() != null && event.getItem().getType() != Material.AIR) {
			return;
		}

		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);
		event.setCancelled(true);

		this.runtime.services().claimStore().getClaimAt(event.getClickedBlock().getLocation())
			.ifPresentOrElse(
				claim -> sendInspectSummary(player, claim),
				() -> player.sendMessage(text("No Safe Zone claim is at that spot.", YELLOW)));
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		this.inspectingPlayers.remove(event.getPlayer().getUniqueId());
	}

	private static void sendInspectSummary(Player player, ClaimData claim) {
		claim.ensureDefaults();
		player.sendMessage(text("Inspect: " + claim.claimId + " owned by " + claim.ownerName, AQUA));
		player.sendMessage(text(
			"Size %dx%d • trusted %d • center %d, %d"
				.formatted(claim.getWidth(), claim.getDepth(), claim.trusted.size(), claim.getCenter().x(), claim.getCenter().z()),
			GRAY));
	}
}
