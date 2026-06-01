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

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import org.jetbrains.annotations.NotNull;

public class InventoryHelper {
    public static @NotNull LithiumStackList getLithiumStackList(LithiumInventory inventory) {
        NonNullList<ItemStack> stackList = inventory.getInventoryLithium();
        if (stackList instanceof LithiumStackList lithiumStackList) {
            return lithiumStackList;
        }
        return upgradeToLithiumStackList(inventory);
    }

    public static LithiumStackList getLithiumStackListOrNull(LithiumInventory inventory) {
        NonNullList<ItemStack> stackList = inventory.getInventoryLithium();
        if (stackList instanceof LithiumStackList lithiumStackList) {
            return lithiumStackList;
        }
        return null;
    }

    private static LithiumStackList upgradeToLithiumStackList(LithiumInventory inventory) {
        //generate loot to avoid any problems with directly accessing the inventory slots
        //the loot that is generated here is not generated earlier than in vanilla, because vanilla generates loot
        //when the hopper checks whether the inventory is empty or full
        inventory.generateLootLithium();
        //get the stack list after generating loot, just in case generating loot creates a new stack list
        NonNullList<ItemStack> stackList = inventory.getInventoryLithium();
        LithiumStackList lithiumStackList = new LithiumStackList(stackList, inventory.getMaxStackSize());
        inventory.setInventoryLithium(lithiumStackList);
        return lithiumStackList;
    }
}
