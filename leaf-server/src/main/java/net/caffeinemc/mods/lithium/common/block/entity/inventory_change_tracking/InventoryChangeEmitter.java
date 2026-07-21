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

/**
 * Interface for Objects that can emit various inventory change events. This does not mean that the inventory
 * creates those events - this requirement is met by InventoryChangeTracker. This distinction is needed due
 * to modded inventories being able to inherit from BaseContainerBlockEntity, which does not guarantee the creation
 * of the required events but implements most of the inventory change listening.
 * The forwarding methods below are helpers, it is not recommended to call them from outside InventoryChangeTracker.java
 */
public interface InventoryChangeEmitter {
    void lithium$emitStackListReplaced();

    void lithium$emitRemoved();

    void lithium$emitContentModified();

    void lithium$emitFirstComparatorAdded();

    void lithium$forwardContentChangeOnce(InventoryChangeListener inventoryChangeListener, LithiumStackList stackList);

    void lithium$forwardMajorInventoryChanges(InventoryChangeListener inventoryChangeListener);

    void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener inventoryChangeListener);

    default void emitCallbackReplaced() {
        this.lithium$emitRemoved();
    }
}
