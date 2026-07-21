/*
 * This file is part of Lithium
 *
 * Lithium is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lithium is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lithium. If not, see <https://www.gnu.org/licenses/>.
 */

package net.caffeinemc.mods.lithium.common.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public interface SleepingBlockEntity {
    TickingBlockEntity SLEEPING_BLOCK_ENTITY_TICKER = new TickingBlockEntity() {
        public void tick() {
        }

        public boolean isRemoved() {
            return false;
        }

        public BlockPos getPos() {
            return null;
        }

        public String getType() {
            return "<lithium_sleeping>";
        }
    };

    LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper();

    void lithium$setTickWrapper(LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper);

    TickingBlockEntity lithium$getSleepingTicker();

    void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker);

    default boolean lithium$startSleeping() {
        if (this.isSleeping()) {
            return false;
        }

        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return false;
        }
        this.lithium$setSleepingTicker(tickWrapper.ticker);
        tickWrapper.rebind(SleepingBlockEntity.SLEEPING_BLOCK_ENTITY_TICKER);
        return true;
    }

    /**
     * To achieve correct timings after waking up the block entity with another block entity, force skipping the block entity ticking for the current tick.
     * This is currently used to make sure hoppers that receive an item and thus go into cooldown handle the cooldown
     * correctly with all possible ticking orders of hoppers.
     */
    default void sleepOnlyCurrentTick() {
        TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (sleepingTicker == null) {
            sleepingTicker = tickWrapper.ticker;
        }
        Level world = ((BlockEntity) this).getLevel();
        tickWrapper.rebind(new SleepUntilTimeBlockEntityTickInvoker((BlockEntity) this, world.getGameTime() + 1, sleepingTicker));
        this.lithium$setSleepingTicker(null); //Important: Prevents anything else from waking up this block entity
    }

    default void wakeUpNow() {
        TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        if (sleepingTicker == null) {
            return;
        }
        this.setTicker(sleepingTicker);
        this.lithium$setSleepingTicker(null);
    }

    default void setTicker(TickingBlockEntity delegate) {
        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return;
        }
        tickWrapper.rebind(delegate);
    }

    default boolean isSleeping() {
        return this.lithium$getSleepingTicker() != null;
    }
}
