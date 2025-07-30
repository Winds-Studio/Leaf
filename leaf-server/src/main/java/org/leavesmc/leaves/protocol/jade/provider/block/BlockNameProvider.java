package org.leavesmc.leaves.protocol.jade.provider.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum BlockNameProvider implements StreamServerDataProvider<BlockAccessor, Component> {
    INSTANCE;

    private static final ResourceLocation CORE_OBJECT_NAME = JadeProtocol.id("object_name");

    @Override
    @Nullable
    public Component streamData(@NotNull BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof Nameable nameable)) {
            return null;
        }
        if (nameable instanceof ChestBlockEntity && accessor.getBlock() instanceof ChestBlock && accessor.getBlockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            MenuProvider menuProvider = accessor.getBlockState().getMenuProvider(accessor.getLevel(), accessor.getPosition());
            if (menuProvider != null) {
                Component name = menuProvider.getDisplayName();
                if (!(name.getContents() instanceof TranslatableContents contents) || !"container.chestDouble".equals(contents.getKey())) {
                    return name;
                }
            }
        } else if (nameable.hasCustomName()) {
            return nameable.getDisplayName();
        }
        return accessor.getBlockEntity().components().get(DataComponents.ITEM_NAME);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
        return ComponentSerialization.STREAM_CODEC;
    }

    @Override
    public ResourceLocation getUid() {
        return CORE_OBJECT_NAME;
    }

    @Override
    public int getDefaultPriority() {
        return -10100;
    }
}