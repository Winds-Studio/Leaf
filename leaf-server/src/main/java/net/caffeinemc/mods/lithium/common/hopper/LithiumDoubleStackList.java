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

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;

/**
 * Class to allow DoubleInventory to have LithiumStackList optimizations.
 * The objects should be immutable and their state should be limited to the first and second inventory.
 * Other state must be managed carefully, as at any time objects of this class may be replaced with new instances.
 */
public class LithiumDoubleStackList extends LithiumStackList {
    private final LithiumStackList first;
    private final LithiumStackList second;
    final LithiumDoubleInventory doubleInventory;

    private long signalStrengthChangeCount;

    public LithiumDoubleStackList(LithiumDoubleInventory doubleInventory, LithiumStackList first, LithiumStackList second, int maxCountPerStack) {
        super(maxCountPerStack);
        this.first = first;
        this.second = second;
        this.doubleInventory = doubleInventory;
    }

    public static LithiumDoubleStackList getOrCreate(LithiumDoubleInventory doubleInventory, LithiumStackList first, LithiumStackList second, int maxCountPerStack) {
        LithiumDoubleStackList parentStackList = first.parent;
        if (parentStackList == null || parentStackList != second.parent || parentStackList.first != first || parentStackList.second != second) {
            if (parentStackList != null) {
                parentStackList.doubleInventory.lithium$emitRemoved();
            }
            parentStackList = new LithiumDoubleStackList(doubleInventory, first, second, maxCountPerStack);
            first.parent = parentStackList;
            second.parent = parentStackList;
        }
        return parentStackList;
    }

    @Override
    public long getModCount() {
        return this.first.getModCount() + this.second.getModCount();
    }

    @Override
    public void changedALot() {
        throw new UnsupportedOperationException("Call changed() on the inventory half only!");
    }

    @Override
    public void changed() {
        throw new UnsupportedOperationException("Call changed() on the inventory half only!");
    }

    @Override
    public ItemStack set(int index, ItemStack element) {
        if (index >= this.first.size()) {
            return this.second.set(index - this.first.size(), element);
        } else {
            return this.first.set(index, element);
        }
    }

    @Override
    public void add(int slot, ItemStack element) {
        throw new UnsupportedOperationException("Call add(int value, ItemStack element) on the inventory half only!");
    }

    @Override
    public ItemStack remove(int index) {
        throw new UnsupportedOperationException("Call remove(int value, ItemStack element) on the inventory half only!");
    }

    @Override
    public void clear() {
        this.first.clear();
        this.second.clear();
    }

    @Override
    public int getSignalStrength(Container inventory) {
        //signal strength override state has to be stored in the halves, because this object may be replaced with a copy at any time
        boolean signalStrengthOverride = this.first.hasSignalStrengthOverride() || this.second.hasSignalStrengthOverride();
        if (signalStrengthOverride) {
            return 0;
        }
        int cachedSignalStrength = this.cachedSignalStrength;
        if (cachedSignalStrength == -1 || this.getModCount() != this.signalStrengthChangeCount) {
            cachedSignalStrength = this.calculateSignalStrength(Integer.MAX_VALUE);
            this.signalStrengthChangeCount = this.getModCount();
            this.cachedSignalStrength = cachedSignalStrength;
            return cachedSignalStrength;
        }
        return cachedSignalStrength;
    }

    @Override
    public void setReducedSignalStrengthOverride() {
        this.first.setReducedSignalStrengthOverride();
        this.second.setReducedSignalStrengthOverride();
    }

    @Override
    public void clearSignalStrengthOverride() {
        this.first.clearSignalStrengthOverride();
        this.second.clearSignalStrengthOverride();
    }

    /**
     * @param masterStackList the stacklist of the inventory that comparators read from (double inventory for double chests)
     * @param inventory       the blockentity / inventory that this stacklist is inside
     */
    public void runComparatorUpdatePatternOnFailedExtract(LithiumStackList masterStackList, Container inventory) {
        if (inventory instanceof CompoundContainer compoundContainer) {
            this.first.runComparatorUpdatePatternOnFailedExtract(
                this, compoundContainer.container1
            );
            this.second.runComparatorUpdatePatternOnFailedExtract(
                this, compoundContainer.container2
            );
        }
    }

    @NotNull
    @Override
    public ItemStack get(int index) {
        return index >= this.first.size() ? this.second.get(index - this.first.size()) : this.first.get(index);
    }

    @Override
    public int size() {
        return this.first.size() + this.second.size();
    }

    public void setInventoryModificationCallback(@NotNull InventoryChangeTracker inventoryModificationCallback) {
        this.first.setInventoryModificationCallback(inventoryModificationCallback);
        this.second.setInventoryModificationCallback(inventoryModificationCallback);
    }

    public void removeInventoryModificationCallback(@NotNull InventoryChangeTracker inventoryModificationCallback) {
        this.first.removeInventoryModificationCallback(inventoryModificationCallback);
        this.second.removeInventoryModificationCallback(inventoryModificationCallback);
    }
}
