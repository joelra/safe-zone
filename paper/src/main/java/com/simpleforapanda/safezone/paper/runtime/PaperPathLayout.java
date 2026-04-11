package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.manager.PersistentStateHelper;
import com.simpleforapanda.safezone.port.PathLayoutPort;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;

public final class PaperPathLayout implements PathLayoutPort {
	private static final String DATA_DIRECTORY_NAME = "data";
	private static final String LOG_DIRECTORY_NAME = "logs";
	private static final String CONFIG_FILE_NAME = "config.json";
	private static final String LEGACY_GAMEPLAY_CONFIG_FILE_NAME = "gameplay_config.json";
	private static final String LEGACY_OPS_SETTINGS_FILE_NAME = "ops_settings.json";
	private static final String LEGACY_CLAIM_SETTINGS_FILE_NAME = "claim_settings.json";
	private static final String NOTIFICATIONS_FILE_NAME = "notifications.json";
	private static final String CLAIMS_FILE_NAME = "claims.json";
	private static final String PLAYER_LIMITS_FILE_NAME = "player_limits.json";
	private static final String STARTER_KIT_RECIPIENTS_FILE_NAME = "starter_kit_recipients.json";
	private static final String CLAIM_SHOW_PREFERENCES_FILE_NAME = "claim_show_preferences.json";
	private static final String AUDIT_LOG_FILE_NAME = "audit.log";

	private final Path pluginDirectory;
	private final Path dataDirectory;
	private final Path logDirectory;

	private PaperPathLayout(Path pluginDirectory) {
		this.pluginDirectory = Objects.requireNonNull(pluginDirectory, "pluginDirectory");
		this.dataDirectory = pluginDirectory.resolve(DATA_DIRECTORY_NAME);
		this.logDirectory = pluginDirectory.resolve(LOG_DIRECTORY_NAME);
	}

	public static PaperPathLayout fromPlugin(JavaPlugin plugin) {
		return new PaperPathLayout(plugin.getDataFolder().toPath());
	}

	@Override
	public void ensureDirectories() {
		PersistentStateHelper.createDataDirectory(this.pluginDirectory);
		PersistentStateHelper.createDataDirectory(this.dataDirectory);
		PersistentStateHelper.createDataDirectory(this.logDirectory);
	}

	@Override
	public Path pluginDirectory() {
		return this.pluginDirectory;
	}

	@Override
	public Path dataDirectory() {
		return this.dataDirectory;
	}

	@Override
	public Path logDirectory() {
		return this.logDirectory;
	}

	@Override
	public Path configFile() {
		return this.pluginDirectory.resolve(CONFIG_FILE_NAME);
	}

	@Override
	public Path legacyGameplayConfigFile() {
		return this.pluginDirectory.resolve(LEGACY_GAMEPLAY_CONFIG_FILE_NAME);
	}

	@Override
	public Path legacyOpsSettingsFile() {
		return this.pluginDirectory.resolve(LEGACY_OPS_SETTINGS_FILE_NAME);
	}

	@Override
	public Path legacyClaimSettingsFile() {
		return this.pluginDirectory.resolve(LEGACY_CLAIM_SETTINGS_FILE_NAME);
	}

	@Override
	public Path notificationsFile() {
		return this.dataDirectory.resolve(NOTIFICATIONS_FILE_NAME);
	}

	@Override
	public Path claimsFile() {
		return this.dataDirectory.resolve(CLAIMS_FILE_NAME);
	}

	@Override
	public Path playerLimitsFile() {
		return this.dataDirectory.resolve(PLAYER_LIMITS_FILE_NAME);
	}

	@Override
	public Path starterKitRecipientsFile() {
		return this.dataDirectory.resolve(STARTER_KIT_RECIPIENTS_FILE_NAME);
	}

	@Override
	public Path claimShowPreferencesFile() {
		return this.dataDirectory.resolve(CLAIM_SHOW_PREFERENCES_FILE_NAME);
	}

	@Override
	public Path auditLogFile() {
		return this.logDirectory.resolve(AUDIT_LOG_FILE_NAME);
	}
}
