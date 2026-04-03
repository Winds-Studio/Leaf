package org.dreeam.leaf.util.cache;

import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

public class CachedItemStackSupplier implements Supplier<ItemStack> {

    private ItemStack cachedCopy;

    public void reset(ItemStack source) {
        this.cachedCopy = source != null ? source.copy() : null;
    }

    @Override
    public ItemStack get() {
        return this.cachedCopy;
    }
}
