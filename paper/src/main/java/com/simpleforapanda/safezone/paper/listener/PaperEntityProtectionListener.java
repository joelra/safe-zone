package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import com.simpleforapanda.safezone.protection.RideableEntityClassifier;
import org.bukkit.Location;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class PaperEntityProtectionListener implements Listener {
	private static final String ADMIN_PERMISSION = "safezone.command.admin";

	private final PaperRuntime runtime;
	private final RideableEntityClassifier<Entity> rideableEntityClassifier;

	public PaperEntityProtectionListener(PaperRuntime runtime) {
		this.runtime = runtime;
		this.rideableEntityClassifier = runtime.services().rideableEntityClassifier();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		if (!isExplosionDamage(event)) {
			return;
		}

		if (shouldBlockExplosionDamage(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// The legacy Bukkit knockback event is deprecated for removal, but it is deliberately
	// used here: for explosion knockback Paper passes it the explosion's direct source
	// entity (the wind charge projectile), whereas the replacement Paper event only
	// carries the attacker (the throwing player/Breeze) — which cannot be distinguished
	// from e.g. a TNT igniter. Cancelling the legacy event propagates to the Paper event.
	// Revisit if a future Paper version removes the legacy event.
	@SuppressWarnings("removal")
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityKnockback(EntityKnockbackEvent event) {
		if (event.getCause() != EntityKnockbackEvent.KnockbackCause.EXPLOSION) {
			return;
		}

		// Wind charges (and Breeze wind bursts) are a movement mechanic, not block
		// griefing — by default their knockback is never suppressed (issues #5 and #6).
		// Admins can set windChargeKnockbackInClaims=false to suppress it for any
		// player standing inside a claim; the wilderness is never affected.
		if (event instanceof EntityKnockbackByEntityEvent byEntity
			&& byEntity.getSourceEntity() instanceof AbstractWindCharge) {
			if (!this.runtime.services().configService().current().gameplay.windChargeKnockbackInClaims
				&& event.getEntity() instanceof Player player
				&& claimStore().getClaimAt(player.getLocation()).isPresent()) {
				event.setCancelled(true);
			}
			return;
		}

		if (shouldBlockExplosionKnockback(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHangingBreak(HangingBreakEvent event) {
		if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
			return;
		}

		if (isProtectedDecorativeEntity(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		handleRideableInteraction(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
		handleRideableInteraction(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
	}

	private boolean shouldBlockExplosionDamage(Entity entity) {
		if (entity instanceof Player player) {
			return hasClaimAccess(player, player.getLocation());
		}

		return isProtectedDecorativeEntity(entity);
	}

	private boolean shouldBlockExplosionKnockback(Entity entity) {
		if (entity instanceof Player player) {
			return hasClaimAccess(player, player.getLocation());
		}

		return isProtectedDecorativeEntity(entity);
	}

	private boolean isProtectedDecorativeEntity(Entity entity) {
		if (!(entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Boat || entity instanceof Minecart)) {
			return false;
		}

		return claimStore().getClaimAt(entity.getLocation()).isPresent();
	}

	private boolean hasClaimAccess(Player player, Location location) {
		Optional<ClaimData> claim = claimStore().getClaimAt(location);
		if (claim.isEmpty()) {
			return false;
		}

		PermissionResult permission = claimStore().getPermission(
			claim.get(),
			player.getUniqueId(),
			player.hasPermission(ADMIN_PERMISSION));
		return permission != PermissionResult.DENIED;
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

	private void handleRideableInteraction(Player player, Entity clicked, EquipmentSlot hand, Cancellable event) {
		if (hand != EquipmentSlot.HAND) {
			return;
		}
		if (!this.rideableEntityClassifier.isRideable(clicked)) {
			return;
		}
		if (!isDenied(player, clicked.getLocation())) {
			return;
		}

		event.setCancelled(true);
		PaperProtectionListener.sendBlockedMessage(player);
	}

	private static boolean isExplosionDamage(EntityDamageEvent event) {
		return switch (event.getCause()) {
			case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> true;
			default -> false;
		};
	}
}
