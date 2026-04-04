package com.simpleforapanda.safezone;

import com.simpleforapanda.safezone.command.AdminCommand;
import com.simpleforapanda.safezone.command.PlayerCommand;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.item.ModItems;
import com.simpleforapanda.safezone.listener.ProtectionListener;
import com.simpleforapanda.safezone.manager.AdminInspectManager;
import com.simpleforapanda.safezone.manager.AuditLogger;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.ClaimVisualizationManager;
import com.simpleforapanda.safezone.manager.ConfigManager;
import com.simpleforapanda.safezone.manager.NotificationManager;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.manager.TitleMessageHelper;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SafeZone implements ModInitializer {
	public static final String MOD_ID = "safe-zone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Safe Zone");
		ModItems.initialize();
		ProtectionListener.register();
		AdminCommand.register();
		PlayerCommand.register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(ClaimVisualizationManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(SafeZone::loadManagers);
		ServerLifecycleEvents.SERVER_STOPPING.register(SafeZone::saveManagers);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> unloadManagers());
	}

	private static void onPlayerJoin(ServerPlayer player) {
		grantStarterKit(player);
		NotificationManager.getInstance().deliverPendingNotifications(player);
	}

	private static void grantStarterKit(ServerPlayer player) {
		ClaimManager claimManager = ClaimManager.getInstance();
		GameplayConfig gameplayConfig = claimManager.getGameplayConfig();
		if (!gameplayConfig.starterKitEnabled || claimManager.hasReceivedStarterKit(player.getUUID())) {
			return;
		}

		ItemStack claimWand = ModItems.createClaimWandStack();
		boolean addedToInventory = player.getInventory().add(claimWand);
		if (!addedToInventory) {
			if (!gameplayConfig.dropStarterKitWhenInventoryFull) {
				PlayerMessageHelper.sendWarning(player,
					SafeZoneText.starterKitInventoryFull(ModItems.claimWandName()));
				return;
			}
			player.drop(claimWand, false);
		}

		claimManager.markStarterKitReceived(player.getUUID());
		String wandName = ModItems.claimWandName();
		TitleMessageHelper.showHint(player, SafeZoneText.STARTER_KIT_READY_TITLE, SafeZoneText.starterKitHoldSubtitle(wandName));
		PlayerMessageHelper.sendStatus(player, "READY", net.minecraft.ChatFormatting.GREEN,
			SafeZoneText.starterKitReady(wandName));
		if (!addedToInventory) {
			PlayerMessageHelper.sendWarning(player, SafeZoneText.starterKitDropped());
		}
		PlayerMessageHelper.sendStep(player, SafeZoneText.starterKitStepOne(wandName));
		PlayerMessageHelper.sendStep(player, SafeZoneText.starterKitStepTwo());
	}

	private static void loadManagers(net.minecraft.server.MinecraftServer server) {
		ConfigManager.getInstance().load(server);
		ClaimManager.getInstance().load(server);
		NotificationManager.getInstance().load(server);
		AuditLogger.getInstance().load(server);
	}

	private static void saveManagers(net.minecraft.server.MinecraftServer server) {
		ConfigManager.getInstance().save();
		ClaimManager.getInstance().save();
		NotificationManager.getInstance().save();
	}

	private static void unloadManagers() {
		ClaimManager.getInstance().unload();
		NotificationManager.getInstance().unload();
		AuditLogger.getInstance().unload();
		ConfigManager.getInstance().unload();
		AdminInspectManager.clear();
	}
}
