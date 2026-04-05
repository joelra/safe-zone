package com.simpleforapanda.safezone.runtime;

import com.simpleforapanda.safezone.item.ClaimWandHandler;
import com.simpleforapanda.safezone.manager.AuditLogger;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.ClaimVisualizationManager;
import com.simpleforapanda.safezone.manager.ConfigManager;
import com.simpleforapanda.safezone.manager.NotificationManager;
import com.simpleforapanda.safezone.protection.FabricRideableEntityClassifier;
import com.simpleforapanda.safezone.protection.RideableEntityClassifier;
import net.minecraft.world.entity.Entity;

public record FabricServices(
	ConfigManager configManager,
	ClaimManager claimManager,
	NotificationManager notificationManager,
	AuditLogger auditLogger,
	FabricAdminInspectService adminInspectService,
	ClaimWandHandler claimWandHandler,
	ClaimVisualizationManager claimVisualizationManager,
	RideableEntityClassifier<Entity> rideableEntityClassifier
) {
	public static FabricServices create() {
		ConfigManager configManager = ConfigManager.getInstance();
		ClaimManager claimManager = ClaimManager.getInstance();
		NotificationManager notificationManager = NotificationManager.getInstance();
		AuditLogger auditLogger = AuditLogger.getInstance();
		FabricAdminInspectService adminInspectService = new FabricAdminInspectService();
		ClaimWandHandler claimWandHandler = new ClaimWandHandler(claimManager);
		ClaimVisualizationManager claimVisualizationManager = new ClaimVisualizationManager(claimManager, claimWandHandler);
		RideableEntityClassifier<Entity> rideableEntityClassifier = new FabricRideableEntityClassifier();
		return new FabricServices(
			configManager,
			claimManager,
			notificationManager,
			auditLogger,
			adminInspectService,
			claimWandHandler,
			claimVisualizationManager,
			rideableEntityClassifier);
	}
}
