package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.item.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ConfigManager {
	private static final Type CONFIG_TYPE = new TypeToken<SafeZoneConfig>() { }.getType();
	private static final Type GAMEPLAY_CONFIG_TYPE = new TypeToken<GameplayConfig>() { }.getType();
	private static final Type OPS_SETTINGS_TYPE = new TypeToken<OpsSettings>() { }.getType();
	private static final String CONFIG_FILE_NAME = "config.json";
	private static final String LEGACY_GAMEPLAY_CONFIG_FILE_NAME = "gameplay_config.json";
	private static final String LEGACY_OPS_SETTINGS_FILE_NAME = "ops_settings.json";
	private static final String LEGACY_CLAIM_SETTINGS_FILE_NAME = "claim_settings.json";
	private static final ConfigManager INSTANCE = new ConfigManager();

	private Path dataDirectory;
	private Path configFilePath;
	private SafeZoneConfig config = new SafeZoneConfig();

	private ConfigManager() {
	}

	public static ConfigManager getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve(SafeZone.MOD_ID);
		Path configFilePath = dataDirectory.resolve(CONFIG_FILE_NAME);
		Path legacyGameplayConfigFilePath = dataDirectory.resolve(LEGACY_GAMEPLAY_CONFIG_FILE_NAME);
		Path legacyOpsSettingsFilePath = dataDirectory.resolve(LEGACY_OPS_SETTINGS_FILE_NAME);
		Path legacyClaimSettingsFilePath = dataDirectory.resolve(LEGACY_CLAIM_SETTINGS_FILE_NAME);

		PersistentStateHelper.createDataDirectory(dataDirectory);
		boolean configExists = Files.exists(configFilePath);
		SafeZoneConfig loadedConfig = configExists
			? PersistentStateHelper.readJson(configFilePath, CONFIG_TYPE, new SafeZoneConfig(), "Safe Zone config", true)
			: loadLegacyConfig(legacyGameplayConfigFilePath, legacyOpsSettingsFilePath);
		loadedConfig.ensureDefaults();
		ModItems.normalizeGameplayConfig(loadedConfig.gameplay);

		this.dataDirectory = dataDirectory;
		this.configFilePath = configFilePath;
		this.config = loadedConfig;

		if (!configExists) {
			save();
		}
		deleteLegacyConfigFiles(legacyGameplayConfigFilePath, legacyOpsSettingsFilePath, legacyClaimSettingsFilePath);

		SafeZone.LOGGER.info(
			"Loaded Safe Zone config from {}: wand={}, defaultMaxClaims={}, maxSize={}x{}, gapEnforced={}, minGap={}, notificationsEnabled={}, notificationRetention={}d, auditLogEnabled={}, mirrorAuditToServerLog={}, createDataBackups={}, recoverFromBackupOnLoadFailure={}",
			this.configFilePath,
			this.config.gameplay.claimWandItemId,
			this.config.gameplay.defaultMaxClaims,
			this.config.gameplay.maxClaimWidth,
			this.config.gameplay.maxClaimDepth,
			this.config.gameplay.claimGapEnforced,
			this.config.gameplay.effectiveMinDistance(),
			this.config.gameplay.notificationsEnabled,
			this.config.gameplay.notificationRetentionDays,
			this.config.ops.auditLogEnabled,
			this.config.ops.mirrorAuditToServerLog,
			this.config.ops.createDataBackups,
			this.config.ops.recoverFromBackupOnLoadFailure);
	}

	public synchronized void save() {
		if (this.configFilePath == null) {
			return;
		}

		PersistentStateHelper.createDataDirectory(this.dataDirectory);
		this.config.ensureDefaults();
		ModItems.normalizeGameplayConfig(this.config.gameplay);
		PersistentStateHelper.writeJsonAtomically(this.configFilePath, this.config, CONFIG_TYPE, "Safe Zone config",
			this.config.ops.createDataBackups, PersistentStateHelper.JsonOutput.PRETTY);
	}

	public synchronized void unload() {
		this.dataDirectory = null;
		this.configFilePath = null;
		this.config = new SafeZoneConfig();
	}

	public synchronized GameplayConfig getGameplayConfig() {
		return this.config.gameplay.copy();
	}

	public synchronized OpsSettings getOpsSettings() {
		return this.config.ops.copy();
	}

	private SafeZoneConfig loadLegacyConfig(Path legacyGameplayConfigFilePath, Path legacyOpsSettingsFilePath) {
		SafeZoneConfig migratedConfig = new SafeZoneConfig();
		migratedConfig.gameplay = PersistentStateHelper.readJson(legacyGameplayConfigFilePath, GAMEPLAY_CONFIG_TYPE,
			new GameplayConfig(), "Safe Zone gameplay config", true);
		migratedConfig.ops = PersistentStateHelper.readJson(legacyOpsSettingsFilePath, OPS_SETTINGS_TYPE,
			new OpsSettings(), "Safe Zone ops settings", true);
		return migratedConfig;
	}

	private void deleteLegacyConfigFiles(Path legacyGameplayConfigFilePath, Path legacyOpsSettingsFilePath,
		Path legacyClaimSettingsFilePath) {
		deleteIfExists(legacyGameplayConfigFilePath);
		deleteIfExists(PersistentStateHelper.backupPathFor(legacyGameplayConfigFilePath));
		deleteIfExists(legacyOpsSettingsFilePath);
		deleteIfExists(PersistentStateHelper.backupPathFor(legacyOpsSettingsFilePath));
		deleteIfExists(legacyClaimSettingsFilePath);
		deleteIfExists(PersistentStateHelper.backupPathFor(legacyClaimSettingsFilePath));
	}

	private static void deleteIfExists(Path filePath) {
		try {
			Files.deleteIfExists(filePath);
		} catch (Exception exception) {
			SafeZone.LOGGER.warn("Failed to delete obsolete Safe Zone config file {}", filePath, exception);
		}
	}
}
