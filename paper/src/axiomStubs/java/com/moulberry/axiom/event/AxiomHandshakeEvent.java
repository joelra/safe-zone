package com.moulberry.axiom.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Compile-only stub for Axiom's AxiomHandshakeEvent.
 * The real implementation is provided by AxiomPaperPlugin at runtime.
 * <p>
 * This event fires when an Axiom client first connects to the server.
 * Cancelling the event prevents the player from using Axiom.
 */
public class AxiomHandshakeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private boolean cancelled = false;

    public AxiomHandshakeEvent(Player player, int maxBufferSize) {
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
