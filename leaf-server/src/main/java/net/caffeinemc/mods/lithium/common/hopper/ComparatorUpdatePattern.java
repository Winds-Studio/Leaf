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

package net.caffeinemc.mods.lithium.common.hopper;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pattern of comparator updates that the given inventory is sending when a hopper
 * unsuccessfully attempts to take items from it. This pattern is independent from
 * the hopper, given that it fails to take items.
 * Background: A hopper trying to take items will take one item out of each slot and
 * put it back right after. Some types of inventories will send comparator updates
 * every time a hopper does that. Multiple consecutive comparator updates
 * are not distinguishable from a single one and can therefore be omitted.
 * <p>
 * A hopper failing to take items can be predicted by testing whether the inventory
 * modification counter {@link LithiumStackList} of the hopper or the inventory above
 * have changed since just before the previous attempt to take items.
 * <p>
 * Decrementing and immediately incrementing the signal strength of an inventory
 * cannot be distinguished from setting the signal strength to 0 temporarily.
 *
 * @author 2No2Name
 */
public enum ComparatorUpdatePattern {
    NO_UPDATE {
        //example: empty inventory, inventory that does not send comparator updates on hopper pull attempt

        @Override
        public ComparatorUpdatePattern thenUpdate() {
            return UPDATE;
        }

        @Override
        public ComparatorUpdatePattern thenDecrementUpdateIncrementUpdate() {
            return DECREMENT_UPDATE_INCREMENT_UPDATE;
        }
    },
    UPDATE {
        //example: inventory with items, but removing any single item does not change the signal strength
        @Override
        public void apply(BlockEntity blockEntity, LithiumStackList stackList) {
            blockEntity.setChanged();
        }

        @Override
        public ComparatorUpdatePattern thenDecrementUpdateIncrementUpdate() {
            return UPDATE_DECREMENT_UPDATE_INCREMENT_UPDATE;
        }
    },
    DECREMENT_UPDATE_INCREMENT_UPDATE {
        //example: inventory with items, but removing the first item reduces the signal strength
        @Override
        public void apply(BlockEntity blockEntity, LithiumStackList stackList) {
            stackList.setReducedSignalStrengthOverride();
            blockEntity.setChanged();
            stackList.clearSignalStrengthOverride();
            blockEntity.setChanged();
        }

        @Override
        public boolean isChainable() {
            return false;
        }
    },
    UPDATE_DECREMENT_UPDATE_INCREMENT_UPDATE {
        //example: inventory with items, removing the first item does not reduce the signal strength,
        // but there is another item that will reduce the signal strength when removed
        @Override
        public void apply(BlockEntity blockEntity, LithiumStackList stackList) {
            blockEntity.setChanged();
            stackList.setReducedSignalStrengthOverride();
            blockEntity.setChanged();
            stackList.clearSignalStrengthOverride();
            blockEntity.setChanged();
        }

        @Override
        public boolean isChainable() {
            return false;
        }
    };

    public void apply(BlockEntity blockEntity, LithiumStackList stackList) {

    }

    public ComparatorUpdatePattern thenUpdate() {
        return this;
    }

    public ComparatorUpdatePattern thenDecrementUpdateIncrementUpdate() {
        return this;
    }

    public boolean isChainable() {
        return true;
    }
}
