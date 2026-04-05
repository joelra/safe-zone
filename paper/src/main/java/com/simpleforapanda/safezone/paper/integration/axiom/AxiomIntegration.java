package com.simpleforapanda.safezone.paper.integration.axiom;

import com.moulberry.axiom.integration.Integration;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Registers Safe Zone's claim-protection hook with the Axiom Paper Plugin's
 * integration API. This class is only referenced (and therefore only loaded)
 * after a runtime check confirms that AxiomPaperPlugin is enabled, preventing
 * {@link ClassNotFoundException} when Axiom is absent.
 */
public final class AxiomIntegration {

    private AxiomIntegration() {}

    /**
     * Returns {@code true} when AxiomPaperPlugin is currently loaded on the
     * server. Callers must invoke this method before calling
     * {@link #register(Plugin, PaperClaimStore)} to avoid loading Axiom
     * classes when the plugin is absent.
     */
    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("AxiomPaper");
    }

    /**
     * Register Safe Zone's {@link SafeZoneAxiomIntegration} with Axiom and
     * the {@link AxiomHandshakeListener} with Bukkit's event bus.
     *
     * <p>Only call after {@link #isPresent()} returns {@code true}.
     */
    public static void register(Plugin plugin, PaperClaimStore claimStore) {
        Integration.registerCustomIntegration(plugin, new SafeZoneAxiomIntegration(claimStore));
        Bukkit.getPluginManager().registerEvents(new AxiomHandshakeListener(), plugin);
        plugin.getLogger().info("Safe Zone: Axiom integration enabled (claim-restricted editing).");
    }
}
