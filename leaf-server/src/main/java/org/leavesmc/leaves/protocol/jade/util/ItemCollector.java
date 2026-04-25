package org.leavesmc.leaves.protocol.jade.util;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ItemCollector<T> {
    public static final int MAX_SIZE = 54;
    public static final ItemCollector<?> EMPTY = new ItemCollector<>(null);
    private static final CompoundTag IGNORED_TAG = new CompoundTag();
    private static final Predicate<ItemStack> SHOWN = stack -> {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()) {
            return false;
        }
        if (stack.hasNonDefault(DataComponents.CUSTOM_MODEL_DATA) || stack.hasNonDefault(DataComponents.ITEM_MODEL)) {
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return !data.matchedBy(IGNORED_TAG);
        }
        return true;
    };
    private final Object2IntLinkedOpenHashMap<ItemDefinition> items = new Object2IntLinkedOpenHashMap<>();
    private final ItemIterator<T> iterator;
    public long version;
    public long lastTimeFinished;
    public boolean lastTimeIsEmpty;
    public List<ViewGroup<ItemStack>> mergedResult;

    static {
        IGNORED_TAG.putBoolean("__JadeClear", true);
    }

    public ItemCollector(ItemIterator<T> iterator) {
        this.iterator = iterator;
    }

    public List<ViewGroup<ItemStack>> update(Accessor<?> request) {
        if (iterator == null) {
            return null;
        }
        T container = iterator.find(request.getTarget());
        if (container == null) {
            return null;
        }
        long currentVersion = iterator.getVersion(container);
        long gameTime = request.getLevel().getServer().getTickCount();
        if (mergedResult != null && iterator.isFinished()) {
            if (version == currentVersion) {
                return mergedResult; // content not changed
            }
            if (lastTimeFinished + 5 > gameTime) {
                return mergedResult; // avoid update too frequently
            }
            iterator.reset();
        }
        AtomicInteger count = new AtomicInteger();
        iterator.populate(container).forEach(stack -> {
            count.incrementAndGet();
            if (SHOWN.test(stack)) {
                ItemDefinition def = new ItemDefinition(stack);
                items.addTo(def, stack.getCount());
            }
        });
        iterator.afterPopulate(container, count.get());
        if (mergedResult != null && !iterator.isFinished()) {
            updateCollectingProgress(mergedResult.getFirst());
            return mergedResult;
        }
        List<ItemStack> partialResult = items.object2IntEntrySet().stream().limit(MAX_SIZE).map(entry -> {
            ItemDefinition def = entry.getKey();
            return def.toStack(entry.getIntValue());
        }).toList();
        List<ViewGroup<ItemStack>> groups = List.of(updateCollectingProgress(new ViewGroup<>(partialResult)));
        if (iterator.isFinished()) {
            mergedResult = groups;
            lastTimeIsEmpty = mergedResult.getFirst().views.isEmpty();
            version = currentVersion;
            lastTimeFinished = gameTime;
            items.clear();
        }
        return groups;
    }

    protected ViewGroup<ItemStack> updateCollectingProgress(ViewGroup<ItemStack> group) {
        if (lastTimeIsEmpty && group.views.isEmpty()) {
            return group;
        }
        float progress = iterator.getCollectingProgress();
        CompoundTag data = group.getExtraData();
        if (Float.isNaN(progress) || progress >= 1) {
            data.remove("Collecting");
        } else {
            data.putFloat("Collecting", progress);
        }
        return group;
    }

    public record ItemDefinition(Item item, DataComponentPatch components) {
        ItemDefinition(ItemStack stack) {
            this(stack.getItem(), stack.getComponentsPatch());
        }

        public ItemStack toStack(int count) {
            ItemStack itemStack = new ItemStack(item, count);
            itemStack.applyComponents(components);
            return itemStack;
        }
    }
}
