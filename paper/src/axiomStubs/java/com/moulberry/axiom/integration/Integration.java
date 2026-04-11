package com.moulberry.axiom.integration;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Compile-only stub for Axiom's Integration class.
 * The real implementation is provided by AxiomPaperPlugin at runtime.
 * <p>
 * Third-party plugins register a {@link CustomIntegration} via
 * {@link #registerCustomIntegration(Plugin, CustomIntegration)} to enforce
 * region/protection constraints for all Axiom block edits.
 */
public class Integration {

    /**
     * Callback interface implemented by Safe Zone to restrict Axiom editing
     * to claim areas the player owns or is trusted in.
     */
    public interface CustomIntegration {
        boolean canBreakBlock(Player player, Block block);
        boolean canPlaceBlock(Player player, Location loc);
        SectionPermissionChecker checkSection(Player player, World world, int cx, int cy, int cz);
    }

    /**
     * Register a custom integration with Axiom. The real implementation is
     * supplied by AxiomPaperPlugin at runtime.
     */
    public static void registerCustomIntegration(Plugin plugin, CustomIntegration custom) {
        // Stub body – replaced by Axiom's real class at runtime.
    }
}
