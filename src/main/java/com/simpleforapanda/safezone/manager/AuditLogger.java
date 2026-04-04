package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.SafeZone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AuditLogger {
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String AUDIT_FILE_NAME = "safe-zone_audit.log";
	private static final AuditLogger INSTANCE = new AuditLogger();

	private Path auditFilePath;
	private boolean auditLogEnabled = true;
	private boolean mirrorAuditToServerLog;

	private AuditLogger() {
	}

	public static AuditLogger getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve(SafeZone.MOD_ID);
		PersistentStateHelper.createDataDirectory(dataDirectory);
		this.auditFilePath = dataDirectory.resolve(AUDIT_FILE_NAME);
		var settings = ConfigManager.getInstance().getOpsSettings();
		this.auditLogEnabled = settings.auditLogEnabled;
		this.mirrorAuditToServerLog = settings.mirrorAuditToServerLog;
	}

	public synchronized void unload() {
		this.auditFilePath = null;
		this.auditLogEnabled = false;
		this.mirrorAuditToServerLog = false;
	}

	public synchronized void logAdminAction(String adminName, String action, String claimId, String details) {
		writeAuditLine("ADMIN", adminName, action, claimId, details);
	}

	public synchronized void logPlayerAction(String playerName, String action, String claimId, String details) {
		writeAuditLine("PLAYER", playerName, action, claimId, details);
	}

	private void writeAuditLine(String actorType, String actorName, String action, String claimId, String details) {
		if (this.auditFilePath == null) {
			SafeZone.LOGGER.warn("Skipped Safe Zone audit event before AuditLogger was attached: actorType={}, action={}, claimId={}",
				actorType, action, claimId);
			return;
		}
		if (!this.auditLogEnabled) {
			return;
		}

		String line = String.format("[%s] %s:%s ACTION:%s CLAIM:%s DETAILS:%s%n",
			LocalDateTime.now().format(TIMESTAMP_FORMATTER),
			actorType,
			sanitize(actorName),
			sanitize(action),
			sanitize(claimId == null ? "-" : claimId),
			sanitize(details));

		if (this.mirrorAuditToServerLog) {
			SafeZone.LOGGER.info("AUDIT {}", line.stripTrailing());
		}

		try {
			Files.writeString(this.auditFilePath, line, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException exception) {
			SafeZone.LOGGER.error("Failed to write Safe Zone audit log to {}", this.auditFilePath, exception);
		}
	}

	private static String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value.replace('\r', ' ').replace('\n', ' ');
	}
}
