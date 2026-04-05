package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.AdminNotification;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class NotificationManager {
	private static final NotificationManager INSTANCE = new NotificationManager();

	private final LongSupplier currentTimeSupplier;
	private final Supplier<GameplayConfig> gameplayConfigSupplier;
	private CommonNotificationService service;

	private NotificationManager() {
		this(System::currentTimeMillis, NotificationManager::currentGameplayConfig);
	}

	NotificationManager(LongSupplier currentTimeSupplier) {
		this(currentTimeSupplier, NotificationManager::currentGameplayConfig);
	}

	NotificationManager(LongSupplier currentTimeSupplier, Supplier<GameplayConfig> gameplayConfigSupplier) {
		this.currentTimeSupplier = Objects.requireNonNull(currentTimeSupplier, "currentTimeSupplier");
		this.gameplayConfigSupplier = Objects.requireNonNull(gameplayConfigSupplier, "gameplayConfigSupplier");
	}

	public static NotificationManager getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		load(FabricPathLayout.fromServer(server), ConfigManager.getInstance().getOpsSettings());
	}

	synchronized void load(Path dataDirectory, boolean recoverFromBackup) {
		OpsSettings opsSettings = new OpsSettings();
		opsSettings.createDataBackups = false;
		opsSettings.recoverFromBackupOnLoadFailure = recoverFromBackup;
		load(new FabricPathLayout(dataDirectory), opsSettings);
	}

	private void load(FabricPathLayout pathLayout, OpsSettings opsSettings) {
		this.service = new CommonNotificationService(pathLayout, this.currentTimeSupplier, message -> SafeZone.LOGGER.info(message), "Safe Zone");
		this.service.load(gameplayConfig(), opsSettings);
	}

	public synchronized void save() {
		if (this.service != null) {
			this.service.save();
		}
	}

	public synchronized void unload() {
		if (this.service != null) {
			this.service.unload();
			this.service = null;
		}
	}

	public synchronized void queueClaimRemovedNotification(ClaimData claim, String adminName) {
		requireService().queueClaimRemovedNotification(claim, adminName);
	}

	public synchronized void queueMessage(String ownerUuid, String ownerName, String adminName, String message) {
		requireService().queueMessage(ownerUuid, ownerName, adminName, message);
	}

	public synchronized void deliverPendingNotifications(ServerPlayer player) {
		if (!notificationsEnabled()) {
			return;
		}
		List<AdminNotification> pendingNotifications = consumePendingNotifications(player.getUUID().toString());
		for (AdminNotification notification : pendingNotifications) {
			PlayerMessageHelper.sendStatus(player, "ADMIN", net.minecraft.ChatFormatting.RED, notification.message);
		}
	}

	synchronized List<AdminNotification> consumePendingNotifications(String ownerUuid) {
		return requireService().consumePendingNotifications(ownerUuid);
	}

	public synchronized int pendingNotificationCount() {
		return this.service == null ? 0 : this.service.pendingNotificationCount();
	}

	public synchronized int purgeAllNotifications() {
		return requireService().purgeAllNotifications();
	}

	public synchronized boolean notificationsEnabled() {
		return gameplayConfig().notificationsEnabled;
	}

	private CommonNotificationService requireService() {
		if (this.service == null) {
			throw new IllegalStateException("NotificationManager is not loaded");
		}
		return this.service;
	}

	private GameplayConfig gameplayConfig() {
		GameplayConfig gameplayConfig = this.gameplayConfigSupplier.get();
		if (gameplayConfig == null) {
			return new GameplayConfig();
		}

		GameplayConfig resolvedConfig = gameplayConfig.copy();
		if (resolvedConfig.notificationRetentionDays < 1) {
			resolvedConfig.notificationRetentionDays = GameplayConfig.DEFAULT_NOTIFICATION_RETENTION_DAYS;
		}
		return resolvedConfig;
	}

	private static GameplayConfig currentGameplayConfig() {
		return ConfigManager.getInstance().getGameplayConfig();
	}
}
