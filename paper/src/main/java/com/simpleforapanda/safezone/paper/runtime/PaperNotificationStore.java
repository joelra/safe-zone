package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.data.AdminNotification;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.CommonNotificationService;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public final class PaperNotificationStore {
	private final CommonNotificationService service;

	PaperNotificationStore(Logger logger, PaperPathLayout paths) {
		this(logger, paths, System::currentTimeMillis);
	}

	PaperNotificationStore(Logger logger, PaperPathLayout paths, LongSupplier currentTimeSupplier) {
		Objects.requireNonNull(logger, "logger");
		this.service = new CommonNotificationService(
			Objects.requireNonNull(paths, "paths"),
			Objects.requireNonNull(currentTimeSupplier, "currentTimeSupplier"),
			logger::info,
			"Safe Zone Paper");
	}

	public synchronized void load(SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		this.service.load(config.gameplay, config.ops);
	}

	public synchronized void save(SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		this.service.save();
	}

	public synchronized int count() {
		return this.service.pendingNotificationCount();
	}

	public synchronized int purgeAllNotifications(SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		return this.service.purgeAllNotifications();
	}

	public synchronized void queueClaimRemovedNotification(ClaimData claim, String adminName, SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		this.service.queueClaimRemovedNotification(claim, adminName);
	}

	public synchronized void queueMessage(String ownerUuid, String ownerName, String adminName, String message, SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		this.service.queueMessage(ownerUuid, ownerName, adminName, message);
	}

	public synchronized void deliverPendingNotifications(Player player, SafeZoneConfig config) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(config, "config");
		if (!this.service.notificationsEnabled()) {
			return;
		}

		List<AdminNotification> pendingNotifications = this.service.consumePendingNotifications(player.getUniqueId().toString());
		if (pendingNotifications.isEmpty()) {
			return;
		}

		player.sendMessage(text("Safe Zone has " + pendingNotifications.size() + " admin update"
			+ (pendingNotifications.size() == 1 ? "" : "s") + " for you.", RED));
		for (AdminNotification notification : pendingNotifications) {
			player.sendMessage(text(notification.message, GRAY));
		}
	}
}
