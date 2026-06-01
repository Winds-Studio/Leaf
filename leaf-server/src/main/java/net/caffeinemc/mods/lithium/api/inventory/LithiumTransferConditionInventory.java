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

public interface LithiumTransferConditionInventory {

    /**
     * Implement this method to signal that the inventory requires a stack size of 1 for item insertion tests.
     * Lithium's hopper optimization transfers a single item, but to avoid excessive copying of item stacks, it passes
     * the original stack to the inventory's insertion test. If the inventory requires a stack size of 1 for this test,
     * the stack should be copied. However, lithium cannot detect whether the copy is necessary and this method is meant
     * to signal this requirement. When the method is not implemented even though it is required, Lithium's hopper
     * optimizations may not transfer items correctly to this inventory.
     * <p>
     * The only vanilla inventory that requires this is the Chiseled Bookshelf. Mods with such special inventories
     * should implement this method in the inventories' class.
     * (It is not required to implement this interface, just the method is enough.)
     *
     * @return whether the inventory requires a stack size of 1 for item insertion tests
     */
    default boolean lithium$itemInsertionTestRequiresStackSize1() {
        return false;
    }

    //TODO default to true and implement this on all inventories which don't need this for improved mod compatibility
}
