package com.simpleforapanda.safezone.paper.integration.fawe;

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

    private FaweIntegration(PaperClaimStore claimStore) {
        this.claimStore = claimStore;
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
        FaweIntegration integration = new FaweIntegration(claimStore);
        WorldEdit.getInstance().getEventBus().register(integration);
        String provider = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
            ? "FastAsyncWorldEdit" : "WorldEdit";
        plugin.getLogger().info("Safe Zone: " + provider + " integration enabled (claim-restricted editing).");
        return integration;
    }

    /**
     * Unregister this extent injector from WorldEdit's global event bus.
     */
    public void unregister() {
        WorldEdit.getInstance().getEventBus().unregister(this);
    }

    /**
     * Null-safe helper for unregistering a previously registered integration.
     */
    public static void unregister(FaweIntegration integration) {
        if (integration != null) {
            integration.unregister();
        }
    }

    /**
     * Called by WorldEdit for every new {@link EditSession}.
     * Wraps the session's extent with {@link SafeZoneFaweExtent} when the
     * actor is a player, intercepting all block changes before they are written.
     */
    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE) {
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
        // Use the world being edited (may differ from the player's current world).
        World editedWorld = event.getWorld() != null
            ? BukkitAdapter.adapt(event.getWorld())
            : player.getWorld();
        event.setExtent(new SafeZoneFaweExtent(event.getExtent(), player, editedWorld, this.claimStore));
    }
}
