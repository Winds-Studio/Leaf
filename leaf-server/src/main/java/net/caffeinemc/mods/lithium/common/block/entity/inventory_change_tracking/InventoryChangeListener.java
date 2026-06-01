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

import net.minecraft.world.Container;

public interface InventoryChangeListener {
    default void handleStackListReplaced(Container inventory) {
        this.lithium$handleInventoryRemoved(inventory);
    }

    void lithium$handleInventoryContentModified(Container inventory);

    void lithium$handleInventoryRemoved(Container inventory);

    /**
     * Propagates an update (comparator added in inventory range)
     *
     * @param inventory the inventory the update is coming from
     * @return Whether the listener unsubscribes due to this update
     */
    boolean lithium$handleComparatorAdded(Container inventory);
}
