package org.leavesmc.leaves.protocol.jade.provider.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum BeehiveProvider implements StreamServerDataProvider<BlockAccessor, Byte> {
    INSTANCE;

    private static final ResourceLocation MC_BEEHIVE = JadeProtocol.mc_id("beehive");

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Byte> streamCodec() {
        return ByteBufCodecs.BYTE.cast();
    }

    @Override
    public Byte streamData(@NotNull BlockAccessor accessor) {
        BeehiveBlockEntity beehive = (BeehiveBlockEntity) accessor.getBlockEntity();
        int bees = beehive.getOccupantCount();
        return (byte) (beehive.isFull() ? bees : -bees);
    }

    @Override
    public ResourceLocation getUid() {
        return MC_BEEHIVE;
    }
}