package org.dreeam.leaf.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * Called when the server is trying to authenticate a joining player.
 * If the event is cancelled, the server won't start authentication for the player.
 * This will make the server behave like an offline server.
 */
@NullMarked
public class AsyncPreAuthenticateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String username;
    private final UUID uuid;
    private final SocketAddress address;
    private boolean cancelled;

    public AsyncPreAuthenticateEvent(final String username, final UUID uuid, final SocketAddress address) {
        super(true);
        this.username = username;
        this.uuid = uuid;
        this.address = address;
    }

    public AsyncPreAuthenticateEvent(final String username, final UUID uuid, final SocketAddress address, final boolean cancelled) {
        this(username, uuid, address);
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Returns the requested username of that player.
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the requested uuid of that player.
     * @return the uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Returns the network address of that player.
     * @return the address
     */
    public SocketAddress getAddress() {
        return address;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
