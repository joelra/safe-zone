package com.simpleforapanda.safezone.paper.integration.fawe;

import com.fastasyncworldedit.core.util.WEManager;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Hooks into WorldEdit / FastAsyncWorldEdit's {@link EditSessionEvent} to
 * inject {@link SafeZoneFaweExtent} for every player-backed edit session.
 *
 * <p>This limits all WorldEdit commands (e.g. {@code //set}, {@code //fill})
 * to claim areas the acting player owns or is trusted in, consistent with the
 * Axiom integration. Admin players ({@code safezone.command.admin}) bypass
 * restrictions.
 *
 * <p>This class must only be loaded after a Bukkit-level check confirms that
 * FastAsyncWorldEdit or WorldEdit is enabled. Referencing this class before
 * the dependency is present may cause a {@link NoClassDefFoundError} because
 * the class body references WorldEdit types in method signatures.
 */
public final class FaweIntegration {

    private final PaperClaimStore claimStore;
    private final boolean faweActive;

    private FaweIntegration(PaperClaimStore claimStore, boolean faweActive) {
        this.claimStore = claimStore;
        this.faweActive = faweActive;
    }

    /**
     * Register the extent injector with WorldEdit's event bus.
     * Only call after confirming WorldEdit or FastAsyncWorldEdit is enabled
     * via a Bukkit-level plugin check.
     *
     * @return the registered integration instance, which should be retained and
     *     passed to {@link #unregister(FaweIntegration)} during plugin shutdown
     */
    public static FaweIntegration register(Plugin plugin, PaperClaimStore claimStore) {
        boolean faweActive = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        FaweIntegration integration = new FaweIntegration(claimStore, faweActive);
        WorldEdit.getInstance().getEventBus().register(integration);
        if (faweActive) {
            // Register a FaweMaskManager so FAWE consults our claim boundaries for
            // every player edit session. This is the correct FAWE 2.x integration
            // point — IBatchProcessor via event.setExtent() is NOT called by FAWE's
            // batch processing queue for externally-set extents.
            // Requires region-restrictions: true in FastAsyncWorldEdit/config.yml.
            WEManager.weManager().addManager(new SafeZoneMaskManager(claimStore));
            plugin.getLogger().info("Safe Zone: FastAsyncWorldEdit integration enabled (claim-restricted editing via FaweMaskManager).");
        } else {
            plugin.getLogger().info("Safe Zone: WorldEdit integration enabled (claim-restricted editing).");
        }
        return integration;
    }

    /**
     * Unregister this extent injector from WorldEdit's global event bus.
     */
    public void unregister() {
        WorldEdit.getInstance().getEventBus().unregister(this);
    }

    /**
     * Called by WorldEdit for every new {@link EditSession}.
     *
     * <p>For plain WorldEdit (no FAWE), wraps the session's extent with
     * {@link SafeZoneFaweExtent} to intercept {@code setBlock()} calls and enforce
     * claim boundaries. For FastAsyncWorldEdit, {@link SafeZoneMaskManager} handles
     * the restriction at the FAWE mask level (FAWE's fast-placement queue bypasses
     * the extent chain entirely, so extent injection is ineffective there).
     */
    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY) {
            return;
        }
        if (this.faweActive) {
            // SafeZoneMaskManager registered with WEManager handles FAWE.
            return;
        }
        Actor actor = event.getActor();
        if (actor == null || !actor.isPlayer()) {
            return;
        }
        Player player = Bukkit.getPlayer(actor.getUniqueId());
        if (player == null) {
            return;
        }
        World editedWorld = event.getWorld() != null
            ? BukkitAdapter.adapt(event.getWorld())
            : player.getWorld();
        event.setExtent(new SafeZoneFaweExtent(event.getExtent(), player, editedWorld, this.claimStore));
    }
}
