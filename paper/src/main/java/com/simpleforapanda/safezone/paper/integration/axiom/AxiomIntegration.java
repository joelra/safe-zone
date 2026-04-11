package com.simpleforapanda.safezone.paper.integration.axiom;

import com.moulberry.axiom.integration.Integration;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Registers Safe Zone's claim-protection hook with the Axiom Paper Plugin's
 * integration API. This class is only referenced (and therefore only loaded)
 * after a pure-Bukkit {@code isPluginEnabled("AxiomPaper")} check in
 * {@code PaperRuntime}, preventing {@link NoClassDefFoundError} when Axiom
 * is absent.
 */
public final class AxiomIntegration {

    private AxiomIntegration() {}

    /**
     * Register Safe Zone's {@link SafeZoneAxiomIntegration} with Axiom and
     * the {@link AxiomHandshakeListener} with Bukkit's event bus.
     *
     * <p>Only call after confirming {@code AxiomPaper} is enabled via the
     * Bukkit plugin manager (without referencing this class first).
     */
    public static void register(Plugin plugin, PaperClaimStore claimStore) {
        Integration.registerCustomIntegration(plugin, new SafeZoneAxiomIntegration(claimStore));
        Bukkit.getPluginManager().registerEvents(new AxiomHandshakeListener(), plugin);
        plugin.getLogger().info("Safe Zone: Axiom integration enabled (claim-restricted editing).");
    }
}
