package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.ClaimExpiryRefreshResult;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import com.simpleforapanda.safezone.text.SafeZoneText;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperStarterKitListener implements Listener {
	private final PaperRuntime runtime;

	public PaperStarterKitListener(PaperRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		SafeZoneConfig config = this.runtime.services().configService().current();
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		ClaimExpiryRefreshResult expiryRefresh = claimStore.refreshOwnerClaimsOnLogin(player.getUniqueId());
		if (expiryRefresh.hasExpiredClaims()) {
			player.sendMessage(text(
				expiryRefresh.expiredClaimIds().size() == 1
					? "One safe zone expired while you were away and was removed."
					: expiryRefresh.expiredClaimIds().size() + " safe zones expired while you were away and were removed.",
				YELLOW));
		}
		this.runtime.services().notificationStore().deliverPendingNotifications(player, config);
		if (!config.gameplay.starterKitEnabled || claimStore.hasReceivedStarterKit(player.getUniqueId())) {
			return;
		}

		grantStarterKit(player, config.gameplay, claimStore);
	}

	private void grantStarterKit(Player player, GameplayConfig gameplayConfig, PaperClaimStore claimStore) {
		ItemStack claimWand = PaperClaimWandSupport.createClaimWand(gameplayConfig);
		Map<Integer, ItemStack> leftovers = player.getInventory().addItem(claimWand);
		boolean addedToInventory = leftovers.isEmpty();
		String wandName = PaperClaimWandSupport.claimWandName(gameplayConfig);
		if (!addedToInventory) {
			if (!gameplayConfig.dropStarterKitWhenInventoryFull) {
				player.sendMessage(text(SafeZoneText.starterKitInventoryFull(wandName), YELLOW));
				return;
			}
			for (ItemStack leftover : leftovers.values()) {
				player.getWorld().dropItem(player.getLocation(), leftover);
			}
		}

		claimStore.markStarterKitReceived(player.getUniqueId());
		PaperTitleMessageHelper.showHint(player, SafeZoneText.STARTER_KIT_READY_TITLE, SafeZoneText.starterKitHoldSubtitle(wandName));
		player.sendMessage(text(SafeZoneText.starterKitReady(wandName), GREEN));
		if (!addedToInventory) {
			player.sendMessage(text(SafeZoneText.starterKitDropped(), YELLOW));
		}
		player.sendMessage(text(SafeZoneText.starterKitStepOne(wandName), GRAY));
		player.sendMessage(text(SafeZoneText.starterKitStepTwo(), GRAY));
	}

}
