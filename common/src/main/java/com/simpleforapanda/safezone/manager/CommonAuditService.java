package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.port.PathLayoutPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CommonAuditService {
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final PathLayoutPort paths;
	private final Supplier<OpsSettings> opsSettingsSupplier;
	private final Consumer<String> infoLogger;
	private final Consumer<String> warningLogger;
	private final Consumer<String> errorLogger;
	private boolean loaded;

	public CommonAuditService(
		PathLayoutPort paths,
		Supplier<OpsSettings> opsSettingsSupplier,
		Consumer<String> infoLogger,
		Consumer<String> warningLogger,
		Consumer<String> errorLogger
	) {
		this.paths = Objects.requireNonNull(paths, "paths");
		this.opsSettingsSupplier = Objects.requireNonNull(opsSettingsSupplier, "opsSettingsSupplier");
		this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
		this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
		this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
	}

	public synchronized void load() {
		this.paths.ensureDirectories();
		this.loaded = true;
	}

	public synchronized void unload() {
		this.loaded = false;
	}

	public synchronized void logAdminAction(String adminName, String action, String claimId, String details) {
		writeAuditLine("ADMIN", adminName, action, claimId, details);
	}

	public synchronized void logPlayerAction(String playerName, String action, String claimId, String details) {
		writeAuditLine("PLAYER", playerName, action, claimId, details);
	}

	private void writeAuditLine(String actorType, String actorName, String action, String claimId, String details) {
		if (!this.loaded) {
			this.warningLogger.accept(
				"Skipped Safe Zone audit event before audit service was attached: actorType=%s, action=%s, claimId=%s"
					.formatted(actorType, action, claimId));
			return;
		}

		OpsSettings settings = resolvedOpsSettings();
		if (!settings.auditLogEnabled) {
			return;
		}

		String line = "[%s] %s:%s ACTION:%s CLAIM:%s DETAILS:%s%n".formatted(
			LocalDateTime.now().format(TIMESTAMP_FORMATTER),
			actorType,
			sanitize(actorName),
			sanitize(action),
			sanitize(claimId == null ? "-" : claimId),
			sanitize(details));

		if (settings.mirrorAuditToServerLog) {
			this.infoLogger.accept("AUDIT " + line.stripTrailing());
		}

		try {
			Files.writeString(
				this.paths.auditLogFile(),
				line,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		} catch (IOException exception) {
			this.errorLogger.accept("Failed to write Safe Zone audit log to " + this.paths.auditLogFile() + ": " + exception.getMessage());
		}
	}

	private OpsSettings resolvedOpsSettings() {
		OpsSettings settings = this.opsSettingsSupplier.get();
		if (settings == null) {
			return new OpsSettings();
		}
		OpsSettings resolved = settings.copy();
		resolved.ensureDefaults();
		return resolved;
	}

	private static String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value.replace('\r', ' ').replace('\n', ' ');
	}
}
