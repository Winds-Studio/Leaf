package org.dreeam.leaf.util.item;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.dreeam.leaf.config.modules.gameplay.HideItemComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ItemStackStripper
 *
 * @author TheFloodDragon
 * @since 2025/2/4 19:04
 */
public class ItemStackStripper {

    public static ItemStack strip(final ItemStack itemStack, final boolean copy) {
        if (!HideItemComponent.enabled || itemStack.isEmpty() || itemStack.getComponentsPatch().isEmpty())
            return itemStack;

        final ItemStack copied = copy ? itemStack.copy() : itemStack;

        // Remove specified types
        for (DataComponentType<?> type : HideItemComponent.hiddenTypes) {
            // Only remove, no others
            copied.remove(type);
        }

        return copied;
    }

    public static List<ItemStack> strip(final List<ItemStack> itemStacks, final boolean copy) {
        if (!HideItemComponent.enabled) return itemStacks;

        final List<ItemStack> copiedItems = new ArrayList<>();

        for (ItemStack itemStack : itemStacks) {
            if (itemStack.isEmpty() || itemStack.getComponentsPatch().isEmpty()) {
                copiedItems.add(itemStack);
                continue;
            }

            final ItemStack copied = copy ? itemStack.copy() : itemStack;

            // Remove specified types
            for (DataComponentType<?> type : HideItemComponent.hiddenTypes) {
                // Only remove, no others
                copied.remove(type);
            }

            copiedItems.add(copied);
        }

        return copiedItems;
    }

    /**
     * Check if two ItemStacks are the same after stripping components
     */
    public static boolean matchesStripped(ItemStack left, ItemStack right) {
        return left == right || (
            left.is(right.getItem()) && left.getCount() == right.getCount() &&
                (left.isEmpty() && right.isEmpty() || Objects.equals(strip(left.getComponents()), strip(right.getComponents())))
        );
    }

    /**
     * @return a new DataComponentMap with all hidden components removed
     */
    private static DataComponentMap strip(final DataComponentMap map) {
        return map.filter(c -> !HideItemComponent.hiddenTypes.contains(c));
    }
}
