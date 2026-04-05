package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.protection.RideableEntityClassifier;
import com.simpleforapanda.safezone.paper.listener.PaperAdminInspectService;
import com.simpleforapanda.safezone.paper.ui.PaperTrustMenuService;
import org.bukkit.entity.Entity;

public record PaperServices(
	PaperConfigService configService,
	PaperNotificationStore notificationStore,
	PaperClaimStore claimStore,
	PaperAuditLogger auditLogger,
	PaperAdminInspectService adminInspectService,
	PaperTrustMenuService trustMenuService,
	RideableEntityClassifier<Entity> rideableEntityClassifier
) {
}
