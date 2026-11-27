package org.dreeam.leaf.util.cache;

import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

public final class CachedItemStackSupplier implements Supplier<ItemStack> {
    private ItemStack source;
    private ItemStack cachedCopy;

    public void reset(ItemStack stack) {
        this.source = stack;
        this.cachedCopy = null;
    }

    @Override
    public ItemStack get() { // The supplier passed down may not always in active use, copy it lazily like Guava's memoize suppliers
        if (this.cachedCopy == null && this.source != null) {
            this.cachedCopy = this.source.copy();
        }
        return this.cachedCopy;
    }
}
