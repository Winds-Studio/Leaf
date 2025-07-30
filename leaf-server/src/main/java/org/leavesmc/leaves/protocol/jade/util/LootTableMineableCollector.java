package org.leavesmc.leaves.protocol.jade.util;

import com.google.common.collect.Lists;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.tool.ShearsToolHandler;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class LootTableMineableCollector {

    private final HolderGetter<LootTable> lootRegistry;
    private final ItemStack toolItem;

    public LootTableMineableCollector(HolderGetter<LootTable> lootRegistry, ItemStack toolItem) {
        this.lootRegistry = lootRegistry;
        this.toolItem = toolItem;
    }

    public static @NotNull List<Block> execute(HolderGetter<LootTable> lootRegistry, ItemStack toolItem) {
        LootTableMineableCollector collector = new LootTableMineableCollector(lootRegistry, toolItem);
        List<Block> list = Lists.newArrayList();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!ShearsToolHandler.getInstance().test(block.defaultBlockState()).isEmpty()) {
                continue;
            }

            if (block.getLootTable().isPresent()) {
                LootTable lootTable = lootRegistry.get(block.getLootTable().get()).map(Holder::value).orElse(null);
                if (collector.doLootTable(lootTable)) {
                    list.add(block);
                }
            }
        }
        return list;
    }

    public static boolean isCorrectConditions(@NotNull List<LootItemCondition> conditions, ItemStack toolItem) {
        if (conditions.size() != 1) {
            return false;
        }

        LootItemCondition condition = conditions.getFirst();
        if (condition instanceof MatchTool(Optional<ItemPredicate> predicate)) {
            ItemPredicate itemPredicate = predicate.orElse(null);
            return itemPredicate != null && itemPredicate.test(toolItem);
        } else if (condition instanceof AnyOfCondition anyOfCondition) {
            for (LootItemCondition child : anyOfCondition.terms) {
                if (isCorrectConditions(List.of(child), toolItem)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doLootTable(LootTable lootTable) {
        if (lootTable == null || lootTable == LootTable.EMPTY) {
            return false;
        }

        for (LootPool pool : lootTable.pools) {
            if (doLootPool(pool)) {
                return true;
            }
        }
        return false;
    }

    private boolean doLootPool(@NotNull LootPool lootPool) {
        for (LootPoolEntryContainer entry : lootPool.entries) {
            if (doLootPoolEntry(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean doLootPoolEntry(LootPoolEntryContainer entry) {
        if (entry instanceof AlternativesEntry alternativesEntry) {
            for (LootPoolEntryContainer child : alternativesEntry.children) {
                if (doLootPoolEntry(child)) {
                    return true;
                }
            }
        } else if (entry instanceof NestedLootTable nestedLootTable) {
            LootTable lootTable = nestedLootTable.contents.map($ -> lootRegistry.get($).map(Holder::value).orElse(null), Function.identity());
            return doLootTable(lootTable);
        } else {
            return isCorrectConditions(entry.conditions, toolItem);
        }
        return false;
    }
}
