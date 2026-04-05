package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.port.PathLayoutPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;

public final class FabricPathLayout implements PathLayoutPort {
	private static final String CONFIG_FILE_NAME = "config.json";
	private static final String LEGACY_GAMEPLAY_CONFIG_FILE_NAME = "gameplay_config.json";
	private static final String LEGACY_OPS_SETTINGS_FILE_NAME = "ops_settings.json";
	private static final String LEGACY_CLAIM_SETTINGS_FILE_NAME = "claim_settings.json";
	private static final String NOTIFICATIONS_FILE_NAME = "notifications.json";
	private static final String CLAIMS_FILE_NAME = "claims.json";
	private static final String PLAYER_LIMITS_FILE_NAME = "player_limits.json";
	private static final String STARTER_KIT_RECIPIENTS_FILE_NAME = "starter_kit_recipients.json";
	private static final String AUDIT_LOG_FILE_NAME = "safe-zone_audit.log";

	private final Path rootDirectory;

	public FabricPathLayout(Path rootDirectory) {
		this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
	}

	public static FabricPathLayout fromServer(MinecraftServer server) {
		return new FabricPathLayout(server.getWorldPath(LevelResource.ROOT).resolve(SafeZone.MOD_ID));
	}

	@Override
	public void ensureDirectories() {
		PersistentStateHelper.createDataDirectory(this.rootDirectory);
	}

	@Override
	public Path pluginDirectory() {
		return this.rootDirectory;
	}

	@Override
	public Path dataDirectory() {
		return this.rootDirectory;
	}

	@Override
	public Path logDirectory() {
		return this.rootDirectory;
	}

	@Override
	public Path configFile() {
		return this.rootDirectory.resolve(CONFIG_FILE_NAME);
	}

	@Override
	public Path legacyGameplayConfigFile() {
		return this.rootDirectory.resolve(LEGACY_GAMEPLAY_CONFIG_FILE_NAME);
	}

	@Override
	public Path legacyOpsSettingsFile() {
		return this.rootDirectory.resolve(LEGACY_OPS_SETTINGS_FILE_NAME);
	}

	@Override
	public Path legacyClaimSettingsFile() {
		return this.rootDirectory.resolve(LEGACY_CLAIM_SETTINGS_FILE_NAME);
	}

	@Override
	public Path notificationsFile() {
		return this.rootDirectory.resolve(NOTIFICATIONS_FILE_NAME);
	}

	@Override
	public Path claimsFile() {
		return this.rootDirectory.resolve(CLAIMS_FILE_NAME);
	}

	@Override
	public Path playerLimitsFile() {
		return this.rootDirectory.resolve(PLAYER_LIMITS_FILE_NAME);
	}

	@Override
	public Path starterKitRecipientsFile() {
		return this.rootDirectory.resolve(STARTER_KIT_RECIPIENTS_FILE_NAME);
	}

	@Override
	public Path auditLogFile() {
		return this.rootDirectory.resolve(AUDIT_LOG_FILE_NAME);
	}
}
