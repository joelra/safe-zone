package com.simpleforapanda.safezone.paper.integration.axiom;

import com.moulberry.axiom.event.AxiomHandshakeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for the Axiom client handshake and denies it for any player who does
 * not have the {@code safezone.axiom} permission (or the admin bypass).
 *
 * <p>This gives server operators a single permission node to control which
 * players may connect with the Axiom editor. Players who do receive the
 * handshake are still limited to editing inside claims they own or are
 * trusted in by {@link SafeZoneAxiomIntegration}.
 */
final class AxiomHandshakeListener implements Listener {

    static final String AXIOM_PERMISSION = "safezone.axiom";
    static final String ADMIN_PERMISSION = SafeZoneAxiomIntegration.ADMIN_PERMISSION;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAxiomHandshake(AxiomHandshakeEvent event) {
        if (event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        if (!event.getPlayer().hasPermission(AXIOM_PERMISSION)) {
            event.setCancelled(true);
        }
    }
}
