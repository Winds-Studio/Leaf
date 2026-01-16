package org.leavesmc.leaves.protocol.jade.provider.block;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum BrewingStandProvider implements StreamServerDataProvider<BlockAccessor, BrewingStandProvider.Data> {
    INSTANCE;

    private static final ResourceLocation MC_BREWING_STAND = JadeProtocol.mc_id("brewing_stand");

    @Override
    public @NotNull Data streamData(@NotNull BlockAccessor accessor) {
        BrewingStandBlockEntity brewingStand = (BrewingStandBlockEntity) accessor.getBlockEntity();
        return new Data(brewingStand.fuel, brewingStand.brewTime);
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC.cast();
    }

    @Override
    public ResourceLocation getUid() {
        return MC_BREWING_STAND;
    }

    public record Data(int fuel, int time) {
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Data::fuel,
            ByteBufCodecs.VAR_INT,
            Data::time,
            Data::new);
    }
}
