package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.CommonAuditService;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class PaperAuditLogger {
	private final CommonAuditService service;

	PaperAuditLogger(Logger logger, PaperPathLayout paths, Supplier<SafeZoneConfig> configSupplier) {
		Objects.requireNonNull(logger, "logger");
		Objects.requireNonNull(configSupplier, "configSupplier");
		this.service = new CommonAuditService(
			Objects.requireNonNull(paths, "paths"),
			() -> resolvedOpsSettings(configSupplier),
			logger::info,
			logger::warning,
			logger::severe);
	}

	public synchronized void load() {
		this.service.load();
	}

	public synchronized void unload() {
		this.service.unload();
	}

	public synchronized void logAdminAction(String adminName, String action, String claimId, String details) {
		this.service.logAdminAction(adminName, action, claimId, details);
	}

	public synchronized void logPlayerAction(String playerName, String action, String claimId, String details) {
		this.service.logPlayerAction(playerName, action, claimId, details);
	}

	private static OpsSettings resolvedOpsSettings(Supplier<SafeZoneConfig> configSupplier) {
		SafeZoneConfig config = configSupplier.get();
		if (config == null) {
			return new OpsSettings();
		}
		OpsSettings resolved = config.ops.copy();
		resolved.ensureDefaults();
		return resolved;
	}
}
