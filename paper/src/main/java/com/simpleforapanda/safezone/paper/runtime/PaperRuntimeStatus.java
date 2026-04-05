package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.data.SafeZoneConfig;

import java.nio.file.Path;

public record PaperRuntimeStatus(
	Path pluginDirectory,
	Path configFile,
	Path dataDirectory,
	Path claimsFile,
	Path playerLimitsFile,
	Path starterKitRecipientsFile,
	Path notificationsFile,
	Path auditLogFile,
	SafeZoneConfig config,
	int claimCount,
	int ownerCount,
	int playerLimitOverrideCount,
	int starterKitRecipientCount,
	int notificationCount
) {
}
