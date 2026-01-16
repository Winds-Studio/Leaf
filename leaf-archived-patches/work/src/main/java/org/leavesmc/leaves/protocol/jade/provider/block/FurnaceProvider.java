package org.leavesmc.leaves.protocol.jade.provider.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

import java.util.List;

public enum FurnaceProvider implements StreamServerDataProvider<BlockAccessor, FurnaceProvider.Data> {
    INSTANCE;

    private static final ResourceLocation MC_FURNACE = JadeProtocol.mc_id("furnace");

    @Override
    public @NotNull Data streamData(@NotNull BlockAccessor accessor) {
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) accessor.getBlockEntity();
        return new Data(
            furnace.cookingTimer,
            furnace.cookingTotalTime,
            List.of(furnace.getItem(0), furnace.getItem(1), furnace.getItem(2))
        );
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public ResourceLocation getUid() {
        return MC_FURNACE;
    }

    public record Data(int progress, int total, List<ItemStack> inventory) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Data::progress,
            ByteBufCodecs.VAR_INT,
            Data::total,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC,
            Data::inventory,
            Data::new);
    }
}
