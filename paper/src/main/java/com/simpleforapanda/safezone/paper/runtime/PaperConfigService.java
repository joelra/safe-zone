package com.simpleforapanda.safezone.paper.runtime;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.PersistentStateHelper;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public final class PaperConfigService {
	private static final Type CONFIG_TYPE = new TypeToken<SafeZoneConfig>() { }.getType();
	private static final Type GAMEPLAY_CONFIG_TYPE = new TypeToken<GameplayConfig>() { }.getType();
	private static final Type OPS_SETTINGS_TYPE = new TypeToken<OpsSettings>() { }.getType();

	private final Logger logger;
	private final PaperPathLayout paths;

	private SafeZoneConfig config = new SafeZoneConfig();

	PaperConfigService(Logger logger, PaperPathLayout paths) {
		this.logger = Objects.requireNonNull(logger, "logger");
		this.paths = Objects.requireNonNull(paths, "paths");
	}

	public synchronized void load() {
		this.paths.ensureDirectories();

		boolean configExists = Files.exists(this.paths.configFile());
		SafeZoneConfig loadedConfig = configExists
			? PersistentStateHelper.readJson(this.paths.configFile(), CONFIG_TYPE, new SafeZoneConfig(), "Safe Zone Paper config", true)
			: loadLegacyConfig();
		loadedConfig.ensureDefaults();
		this.config = loadedConfig;

		if (!configExists) {
			save();
		}
		deleteLegacyConfigFiles();
		logConfigSummary();
	}

	public synchronized void save() {
		this.paths.ensureDirectories();
		this.config.ensureDefaults();
		PersistentStateHelper.writeJsonAtomically(
			this.paths.configFile(),
			this.config,
			CONFIG_TYPE,
			"Safe Zone Paper config",
			this.config.ops.createDataBackups,
			PersistentStateHelper.JsonOutput.PRETTY);
	}

	public synchronized SafeZoneConfig current() {
		return this.config.copy();
	}

	private SafeZoneConfig loadLegacyConfig() {
		SafeZoneConfig migratedConfig = new SafeZoneConfig();
		migratedConfig.gameplay = PersistentStateHelper.readJson(
			this.paths.legacyGameplayConfigFile(),
			GAMEPLAY_CONFIG_TYPE,
			new GameplayConfig(),
			"Safe Zone Paper gameplay config",
			true);
		migratedConfig.ops = PersistentStateHelper.readJson(
			this.paths.legacyOpsSettingsFile(),
			OPS_SETTINGS_TYPE,
			new OpsSettings(),
			"Safe Zone Paper ops settings",
			true);
		return migratedConfig;
	}

	private void deleteLegacyConfigFiles() {
		deleteIfExists(this.paths.legacyGameplayConfigFile());
		deleteIfExists(PersistentStateHelper.backupPathFor(this.paths.legacyGameplayConfigFile()));
		deleteIfExists(this.paths.legacyOpsSettingsFile());
		deleteIfExists(PersistentStateHelper.backupPathFor(this.paths.legacyOpsSettingsFile()));
		deleteIfExists(this.paths.legacyClaimSettingsFile());
		deleteIfExists(PersistentStateHelper.backupPathFor(this.paths.legacyClaimSettingsFile()));
	}

	private void deleteIfExists(Path filePath) {
		try {
			Files.deleteIfExists(filePath);
		} catch (Exception exception) {
			this.logger.warning("Failed to delete obsolete Safe Zone Paper file " + filePath + ": " + exception.getMessage());
		}
	}

	private void logConfigSummary() {
		this.logger.info(
			"Loaded Safe Zone Paper config from %s: wand=%s, defaultMaxClaims=%d, maxSize=%dx%d, gapEnforced=%s, minGap=%d, notificationsEnabled=%s, notificationRetention=%dd, auditLogEnabled=%s, mirrorAuditToServerLog=%s, createDataBackups=%s, recoverFromBackupOnLoadFailure=%s"
				.formatted(
					this.paths.configFile(),
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
					this.config.ops.recoverFromBackupOnLoadFailure));
	}
}
