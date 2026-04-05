package com.simpleforapanda.safezone.port;

import java.nio.file.Path;

public interface PathLayoutPort {
	void ensureDirectories();

	Path pluginDirectory();

	Path dataDirectory();

	Path logDirectory();

	Path configFile();

	Path legacyGameplayConfigFile();

	Path legacyOpsSettingsFile();

	Path legacyClaimSettingsFile();

	Path notificationsFile();

	Path claimsFile();

	Path playerLimitsFile();

	Path starterKitRecipientsFile();

	Path auditLogFile();
}
