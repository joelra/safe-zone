package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.data.AdminNotification;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.port.PathLayoutPort;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class CommonNotificationService {
	private static final Type NOTIFICATION_LIST_TYPE = new TypeToken<List<AdminNotification>>() { }.getType();

	private final PathLayoutPort paths;
	private final LongSupplier currentTimeSupplier;
	private final Consumer<String> infoLogger;
	private final List<AdminNotification> notifications = new ArrayList<>();
	private final String labelPrefix;

	private GameplayConfig gameplayConfig = new GameplayConfig();
	private OpsSettings opsSettings = new OpsSettings();
	private boolean loaded;

	public CommonNotificationService(
		PathLayoutPort paths,
		LongSupplier currentTimeSupplier,
		Consumer<String> infoLogger,
		String labelPrefix
	) {
		this.paths = Objects.requireNonNull(paths, "paths");
		this.currentTimeSupplier = Objects.requireNonNull(currentTimeSupplier, "currentTimeSupplier");
		this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
		this.labelPrefix = Objects.requireNonNull(labelPrefix, "labelPrefix");
	}

	public synchronized void load(GameplayConfig gameplayConfig, OpsSettings opsSettings) {
		Objects.requireNonNull(gameplayConfig, "gameplayConfig");
		Objects.requireNonNull(opsSettings, "opsSettings");
		this.paths.ensureDirectories();
		this.gameplayConfig = gameplayConfig.copy();
		this.opsSettings = opsSettings.copy();

		List<AdminNotification> loadedNotifications = PersistentStateHelper.readJson(
			this.paths.notificationsFile(),
			NOTIFICATION_LIST_TYPE,
			List.of(),
			this.labelPrefix + " notifications",
			this.opsSettings.recoverFromBackupOnLoadFailure);

		this.notifications.clear();
		this.notifications.addAll(loadedNotifications);
		this.loaded = true;
		if (!notificationsEnabled()) {
			int discardedNotifications = this.notifications.size();
			this.notifications.clear();
			save();
			this.infoLogger.accept("Loaded 0 pending notifications because notifications are disabled (discarded %d)."
				.formatted(discardedNotifications));
			return;
		}

		int expiredNotifications = pruneExpiredNotifications(this.currentTimeSupplier.getAsLong());
		if (expiredNotifications > 0) {
			save();
		}
		this.infoLogger.accept("Loaded %d pending notifications (pruned %d)."
			.formatted(this.notifications.size(), expiredNotifications));
	}

	public synchronized void save() {
		if (!this.loaded) {
			return;
		}

		this.paths.ensureDirectories();
		PersistentStateHelper.writeJsonAtomically(
			this.paths.notificationsFile(),
			this.notifications,
			NOTIFICATION_LIST_TYPE,
			this.labelPrefix + " notifications",
			this.opsSettings.createDataBackups,
			PersistentStateHelper.JsonOutput.COMPACT);
	}

	public synchronized void unload() {
		this.notifications.clear();
		this.gameplayConfig = new GameplayConfig();
		this.opsSettings = new OpsSettings();
		this.loaded = false;
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

	public synchronized void queueClaimRemovedNotification(ClaimData claim, String adminName) {
		Objects.requireNonNull(claim, "claim");
		var center = claim.getCenter();
		queueMessage(
			claim.ownerUuid,
			claim.ownerName,
			adminName,
			"An admin removed your area at " + center.x() + ", " + center.z() + ".");
	}

	public synchronized void queueMessage(String ownerUuid, String ownerName, String adminName, String message) {
		if (!notificationsEnabled()) {
			return;
		}
		pruneExpiredNotifications(this.currentTimeSupplier.getAsLong());
		this.notifications.add(new AdminNotification(ownerUuid, ownerName, adminName, message, this.currentTimeSupplier.getAsLong()));
		save();
	}

	public synchronized List<AdminNotification> consumePendingNotifications(String ownerUuid) {
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

	public synchronized boolean notificationsEnabled() {
		return this.gameplayConfig.notificationsEnabled;
	}

	private int pruneExpiredNotifications(long now) {
		long retentionMillis = this.gameplayConfig.notificationRetentionMillis();
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
