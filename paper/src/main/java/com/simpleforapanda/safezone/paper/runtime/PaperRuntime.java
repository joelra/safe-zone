package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.paper.integration.axiom.AxiomIntegration;
import com.simpleforapanda.safezone.paper.integration.fawe.FaweIntegration;
import com.simpleforapanda.safezone.paper.listener.PaperAdminInspectService;
import com.simpleforapanda.safezone.paper.listener.PaperClaimVisualizationService;
import com.simpleforapanda.safezone.paper.listener.PaperClaimWandListener;
import com.simpleforapanda.safezone.paper.listener.PaperClaimWandState;
import com.simpleforapanda.safezone.paper.listener.PaperEntityProtectionListener;
import com.simpleforapanda.safezone.paper.listener.PaperProtectionListener;
import com.simpleforapanda.safezone.paper.listener.PaperStarterKitListener;
import com.simpleforapanda.safezone.paper.protection.PaperRideableEntityClassifier;
import com.simpleforapanda.safezone.paper.ui.PaperTrustMenuService;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperRuntime {
	private final PaperPluginContext context;
	private final PaperServices services;
	private final PaperCommandRegistrar commandRegistrar;
	private final PaperClaimVisualizationService claimVisualizationService;
	private final PaperClaimWandListener claimWandListener;

	private PaperRuntime(PaperPluginContext context) {
		this.context = context;
		PaperConfigService configService = new PaperConfigService(context.logger(), context.paths());
		PaperNotificationStore notificationStore = new PaperNotificationStore(context.logger(), context.paths());
		PaperClaimStore claimStore = new PaperClaimStore(context.logger(), context.paths());
		PaperAdminInspectService adminInspectService = new PaperAdminInspectService(this);
		PaperClaimWandState claimWandState = new PaperClaimWandState();
		this.services = new PaperServices(
			configService,
			notificationStore,
			claimStore,
			new PaperAuditLogger(context.logger(), context.paths(), configService::current),
			adminInspectService,
			new PaperTrustMenuService(claimStore),
			new PaperRideableEntityClassifier());
		this.commandRegistrar = new PaperCommandRegistrar(context.plugin());
		this.claimVisualizationService = new PaperClaimVisualizationService(context.plugin(), this, claimWandState);
		this.claimWandListener = new PaperClaimWandListener(this, claimWandState);
	}

	public static PaperRuntime create(JavaPlugin plugin) {
		return new PaperRuntime(PaperPluginContext.create(plugin));
	}

	public void start() {
		this.context.paths().ensureDirectories();
		this.services.configService().load();
		this.services.auditLogger().load();
		SafeZoneConfig config = this.services.configService().current();
		this.services.claimStore().load(config);
		this.services.notificationStore().load(config);
		this.services.adminInspectService().clear();
		this.commandRegistrar.register(this);
		this.context.plugin().getServer().getPluginManager().registerEvents(new PaperStarterKitListener(this), this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(this.services.adminInspectService(), this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(this.claimWandListener, this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(this.claimVisualizationService, this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(new PaperProtectionListener(this), this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(new PaperEntityProtectionListener(this), this.context.plugin());
		this.context.plugin().getServer().getPluginManager().registerEvents(this.services.trustMenuService(), this.context.plugin());
		this.claimVisualizationService.start();
		if (AxiomIntegration.isPresent()) {
			AxiomIntegration.register(this.context.plugin(), this.services.claimStore());
		}
		if (FaweIntegration.isPresent()) {
			FaweIntegration.register(this.context.plugin(), this.services.claimStore());
		}
		this.context.logger().info("Safe Zone Paper runtime enabled at " + this.context.paths().pluginDirectory());
	}

	public void stop() {
		SafeZoneConfig config = this.services.configService().current();
		this.services.adminInspectService().clear();
		this.claimVisualizationService.stop();
		this.services.claimStore().save();
		this.services.notificationStore().save(config);
		this.services.configService().save();
		this.services.auditLogger().unload();
		this.context.logger().info("Safe Zone Paper runtime disabled");
	}

	public void reload() {
		this.services.configService().load();
		SafeZoneConfig config = this.services.configService().current();
		this.services.claimStore().load(config);
		this.services.notificationStore().load(config);
		this.services.adminInspectService().clear();
		this.claimVisualizationService.clearAll();
	}

	public PaperServices services() {
		return this.services;
	}

	public PaperRuntimeStatus status() {
		SafeZoneConfig config = this.services.configService().current();
		return new PaperRuntimeStatus(
			this.context.paths().pluginDirectory(),
			this.context.paths().configFile(),
			this.context.paths().dataDirectory(),
			this.context.paths().claimsFile(),
			this.context.paths().playerLimitsFile(),
			this.context.paths().starterKitRecipientsFile(),
			this.context.paths().notificationsFile(),
			this.context.paths().auditLogFile(),
			config,
			this.services.claimStore().countClaims(),
			this.services.claimStore().countOwners(),
			this.services.claimStore().countPlayerLimitOverrides(),
			this.services.claimStore().countStarterKitRecipients(),
			this.services.notificationStore().count());
	}
}
