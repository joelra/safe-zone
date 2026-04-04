package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.AdminNotification;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class NotificationManager {
	private static final Type NOTIFICATION_LIST_TYPE = new TypeToken<List<AdminNotification>>() { }.getType();
	private static final String NOTIFICATIONS_FILE_NAME = "notifications.json";
	private static final NotificationManager INSTANCE = new NotificationManager();

	private final List<AdminNotification> notifications = new ArrayList<>();
	private final LongSupplier currentTimeSupplier;
	private final Supplier<GameplayConfig> gameplayConfigSupplier;

	private Path dataDirectory;
	private Path notificationsFilePath;

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
		boolean recoverFromBackup = ConfigManager.getInstance().getOpsSettings().recoverFromBackupOnLoadFailure;
		load(server.getWorldPath(LevelResource.ROOT).resolve(SafeZone.MOD_ID), recoverFromBackup);
	}

	synchronized void load(Path dataDirectory, boolean recoverFromBackup) {
		Path notificationsFilePath = dataDirectory.resolve(NOTIFICATIONS_FILE_NAME);
		PersistentStateHelper.createDataDirectory(dataDirectory);
		List<AdminNotification> loadedNotifications = new ArrayList<>(readJson(notificationsFilePath, List.of(),
			"Safe Zone notifications", recoverFromBackup));

		this.dataDirectory = dataDirectory;
		this.notificationsFilePath = notificationsFilePath;
		this.notifications.clear();
		this.notifications.addAll(loadedNotifications);
		if (!notificationsEnabled()) {
			int discardedNotifications = this.notifications.size();
			this.notifications.clear();
			save();
			SafeZone.LOGGER.info("Loaded 0 pending Safe Zone notifications because notifications are disabled (discarded {}).",
				discardedNotifications);
			return;
		}
		int expiredNotifications = pruneExpiredNotifications(this.currentTimeSupplier.getAsLong());
		if (expiredNotifications > 0) {
			save();
		}
		SafeZone.LOGGER.info("Loaded {} pending Safe Zone notifications (pruned {}).", this.notifications.size(),
			expiredNotifications);
	}

	public synchronized void save() {
		if (this.notificationsFilePath == null) {
			return;
		}

		boolean createBackups = ConfigManager.getInstance().getOpsSettings().createDataBackups;
		PersistentStateHelper.createDataDirectory(this.dataDirectory);
		writeJson(this.notifications, NOTIFICATION_LIST_TYPE, "Safe Zone notifications", createBackups);
	}

	public synchronized void unload() {
		this.dataDirectory = null;
		this.notificationsFilePath = null;
		this.notifications.clear();
	}

	public synchronized void queueClaimRemovedNotification(ClaimData claim, String adminName) {
		if (!notificationsEnabled()) {
			return;
		}
		pruneExpiredNotifications(this.currentTimeSupplier.getAsLong());
		this.notifications.add(new AdminNotification(
			claim.ownerUuid,
			claim.ownerName,
			adminName,
			"An admin removed your area at " + claim.getCenter().getX() + ", " + claim.getCenter().getZ() + ".",
			this.currentTimeSupplier.getAsLong()
		));
		save();
	}

	public synchronized void queueMessage(String ownerUuid, String ownerName, String adminName, String message) {
		if (!notificationsEnabled()) {
			return;
		}
		pruneExpiredNotifications(this.currentTimeSupplier.getAsLong());
		this.notifications.add(new AdminNotification(ownerUuid, ownerName, adminName, message, this.currentTimeSupplier.getAsLong()));
		save();
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
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		long now = this.currentTimeSupplier.getAsLong();
		boolean changed = pruneExpiredNotifications(now) > 0;
		List<AdminNotification> deliveredNotifications = new ArrayList<>();
		Iterator<AdminNotification> iterator = this.notifications.iterator();
		while (iterator.hasNext()) {
			AdminNotification notification = iterator.next();
			if (!ownerUuid.equals(notification.ownerUuid)) {
				continue;
			}

			deliveredNotifications.add(notification);
			iterator.remove();
			changed = true;
		}

		if (changed) {
			save();
		}
		return deliveredNotifications;
	}

	public synchronized int pendingNotificationCount() {
		return this.notifications.size();
	}

	public synchronized int purgeAllNotifications() {
		int purgedNotifications = this.notifications.size();
		this.notifications.clear();
		save();
		return purgedNotifications;
	}

	public synchronized boolean notificationsEnabled() {
		return gameplayConfig().notificationsEnabled;
	}

	private List<AdminNotification> readJson(Path filePath, List<AdminNotification> fallback, String dataLabel, boolean recoverFromBackup) {
		return PersistentStateHelper.readJson(filePath, NOTIFICATION_LIST_TYPE, fallback, dataLabel, recoverFromBackup);
	}

	private void writeJson(Object value, Type type, String dataLabel, boolean createBackup) {
		PersistentStateHelper.writeJsonAtomically(this.notificationsFilePath, value, type, dataLabel, createBackup,
			PersistentStateHelper.JsonOutput.COMPACT);
	}

	private int pruneExpiredNotifications(long now) {
		long retentionMillis = notificationRetentionMillis();
		int removedCount = 0;
		Iterator<AdminNotification> iterator = this.notifications.iterator();
		while (iterator.hasNext()) {
			if (!shouldRetain(iterator.next(), now, retentionMillis)) {
				iterator.remove();
				removedCount++;
			}
		}
		return removedCount;
	}

	private long notificationRetentionMillis() {
		return gameplayConfig().notificationRetentionMillis();
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

	private static boolean shouldRetain(AdminNotification notification, long now, long retentionMillis) {
		if (notification == null || isBlank(notification.ownerUuid) || isBlank(notification.message) || notification.timestamp <= 0L) {
			return false;
		}

		return now < notification.timestamp || now - notification.timestamp < retentionMillis;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
