package org.leavesmc.leaves.protocol.jade.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ShearsToolHandler {

    private static final ShearsToolHandler INSTANCE = new ShearsToolHandler();

    private final List<ItemStack> tools;

    public ShearsToolHandler() {
        this.tools = List.of(Items.SHEARS.getDefaultInstance());
    }

    public static ShearsToolHandler getInstance() {
        return INSTANCE;
    }

    public ItemStack test(BlockState state) {
        for (ItemStack toolItem : tools) {
            if (toolItem.isCorrectToolForDrops(state)) {
                return toolItem;
            }
            Tool tool = toolItem.get(DataComponents.TOOL);
            if (tool != null && tool.getMiningSpeed(state) > tool.defaultMiningSpeed()) {
                return toolItem;
            }
        }
        return ItemStack.EMPTY;
    }
}
