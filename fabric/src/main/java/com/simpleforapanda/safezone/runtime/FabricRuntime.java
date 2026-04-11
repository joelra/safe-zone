package com.simpleforapanda.safezone.runtime;

import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.manager.ClaimExpiryRefreshResult;
import com.simpleforapanda.safezone.manager.FabricPathLayout;
import com.simpleforapanda.safezone.manager.PersistentStateHelper;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.manager.TitleMessageHelper;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class FabricRuntime {
	private final FabricServices services;

	private FabricRuntime(FabricServices services) {
		this.services = services;
	}

	public static FabricRuntime create() {
		return new FabricRuntime(FabricServices.create());
	}

	public void start(MinecraftServer server) {
		FabricPathLayout paths = FabricPathLayout.fromServer(server);
		PersistentStateHelper.cleanupStaleTempFile(paths.claimsFile());
		PersistentStateHelper.cleanupStaleTempFile(paths.playerLimitsFile());
		PersistentStateHelper.cleanupStaleTempFile(paths.starterKitRecipientsFile());
		PersistentStateHelper.cleanupStaleTempFile(paths.notificationsFile());
		this.services.configManager().load(server);
		this.services.claimManager().load(server);
		this.services.notificationManager().load(server);
		this.services.auditLogger().load(server);
		this.services.adminInspectService().clear();
	}

	public void save() {
		this.services.configManager().save();
		this.services.claimManager().save();
		this.services.notificationManager().save();
	}

	public void stop() {
		this.services.claimManager().unload();
		this.services.notificationManager().unload();
		this.services.auditLogger().unload();
		this.services.configManager().unload();
		this.services.adminInspectService().clear();
	}

	public void reload(MinecraftServer server) {
		start(server);
	}

	public void tick(MinecraftServer server) {
		this.services.claimVisualizationManager().tick(server);
	}

	public void onPlayerJoin(ServerPlayer player) {
		this.services.claimWandHandler().clearPlayer(player.getUUID());
		this.services.claimVisualizationManager().clearPlayer(player);
		ClaimExpiryRefreshResult expiryRefresh = this.services.claimManager().refreshOwnerClaimsOnLogin(player.getUUID());
		if (expiryRefresh.hasExpiredClaims()) {
			PlayerMessageHelper.sendWarning(player, SafeZoneText.claimExpiryRemovedNotice(expiryRefresh.expiredClaimIds().size()));
		}
		grantStarterKit(player);
		this.services.notificationManager().deliverPendingNotifications(player);
	}

	public FabricServices services() {
		return this.services;
	}

	public void onPlayerLeave(ServerPlayer player) {
		this.services.claimWandHandler().clearPlayer(player.getUUID());
		this.services.claimVisualizationManager().clearPlayer(player);
	}

	private void grantStarterKit(ServerPlayer player) {
		GameplayConfig gameplayConfig = this.services.claimManager().getGameplayConfig();
		if (!gameplayConfig.starterKitEnabled || this.services.claimManager().hasReceivedStarterKit(player.getUUID())) {
			return;
		}

		ItemStack claimWand = this.services.claimWandHandler().createClaimWandStack();
		boolean addedToInventory = player.getInventory().add(claimWand);
		if (!addedToInventory) {
			if (!gameplayConfig.dropStarterKitWhenInventoryFull) {
				PlayerMessageHelper.sendWarning(player,
					SafeZoneText.starterKitInventoryFull(this.services.claimWandHandler().wandName()));
				return;
			}
			player.drop(claimWand, false);
		}

		this.services.claimManager().markStarterKitReceived(player.getUUID());
		String wandName = this.services.claimWandHandler().wandName();
		TitleMessageHelper.showHint(player, SafeZoneText.STARTER_KIT_READY_TITLE, SafeZoneText.starterKitHoldSubtitle(wandName));
		PlayerMessageHelper.sendStatus(player, "READY", net.minecraft.ChatFormatting.GREEN,
			SafeZoneText.starterKitReady(wandName));
		if (!addedToInventory) {
			PlayerMessageHelper.sendWarning(player, SafeZoneText.starterKitDropped());
		}
		PlayerMessageHelper.sendStep(player, SafeZoneText.starterKitStepOne(wandName));
		PlayerMessageHelper.sendStep(player, SafeZoneText.starterKitStepTwo());
	}
}
