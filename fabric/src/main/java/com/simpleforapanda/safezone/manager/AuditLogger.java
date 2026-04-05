package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.OpsSettings;
import net.minecraft.server.MinecraftServer;

public final class AuditLogger {
	private static final AuditLogger INSTANCE = new AuditLogger();

	private CommonAuditService service;

	private AuditLogger() {
	}

	public static AuditLogger getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		this.service = new CommonAuditService(
			FabricPathLayout.fromServer(server),
			ConfigManager.getInstance()::getOpsSettings,
			message -> SafeZone.LOGGER.info(message),
			message -> SafeZone.LOGGER.warn(message),
			message -> SafeZone.LOGGER.error(message));
		this.service.load();
	}

	public synchronized void unload() {
		if (this.service != null) {
			this.service.unload();
			this.service = null;
		}
	}

	public synchronized void logAdminAction(String adminName, String action, String claimId, String details) {
		requireService().logAdminAction(adminName, action, claimId, details);
	}

	public synchronized void logPlayerAction(String playerName, String action, String claimId, String details) {
		requireService().logPlayerAction(playerName, action, claimId, details);
	}

	private CommonAuditService requireService() {
		if (this.service == null) {
			throw new IllegalStateException("AuditLogger is not loaded");
		}
		return this.service;
	}
}
