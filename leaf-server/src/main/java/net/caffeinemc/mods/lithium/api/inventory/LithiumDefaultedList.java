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

package net.caffeinemc.mods.lithium.api.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public interface LithiumDefaultedList {
    /**
     * Call this method when the behavior of
     * {@link net.minecraft.world.Container#canPlaceItem(int, ItemStack)}
     * {@link net.minecraft.world.WorldlyContainer#canPlaceItemThroughFace(int, ItemStack, Direction)}
     * {@link net.minecraft.world.WorldlyContainer#canTakeItemThroughFace(int, ItemStack, Direction)}
     * or similar functionality changed.
     * This method will not need to be called if this change in behavior is triggered by a change of the stack list contents.
     */
    void changedInteractionConditions();
}
