package org.dreeam.leaf.event;

import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Called when a block executes its explosion hit actions.
 * If the event is cancelled, the block will not execute the explosion hit actions.
 */
@NullMarked
public class BlockExplosionHitEvent extends BlockEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Entity source;
    private final ExplosionResult result;

    public BlockExplosionHitEvent(final Block block, final Entity source, final ExplosionResult result) {
        super(block);
        this.source = source;
        this.result = result;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
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
     * Returns the entity responsible for the explosion.
     *
     * @return Entity responsible for the explosion
     */
    public Entity getSource() {
        return source;
    }

    /**
     * Returns the result of the explosion.
     *
     * @return the result of the explosion
     */
    public ExplosionResult getResult() {
        return result;
    }
}
