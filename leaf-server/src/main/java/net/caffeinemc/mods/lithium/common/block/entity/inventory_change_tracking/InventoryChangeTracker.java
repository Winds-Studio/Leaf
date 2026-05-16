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

package net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking;

import net.caffeinemc.mods.lithium.common.hopper.LithiumStackList;

public interface InventoryChangeTracker extends InventoryChangeEmitter {
    default void listenForContentChangesOnce(LithiumStackList stackList, InventoryChangeListener inventoryChangeListener) {
        this.lithium$forwardContentChangeOnce(inventoryChangeListener, stackList);
    }

    default void listenForMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        this.lithium$forwardMajorInventoryChanges(inventoryChangeListener);
    }

    default void stopListenForMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        this.lithium$stopForwardingMajorInventoryChanges(inventoryChangeListener);
    }
}
