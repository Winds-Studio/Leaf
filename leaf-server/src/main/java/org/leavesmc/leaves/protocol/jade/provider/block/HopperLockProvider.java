package org.leavesmc.leaves.protocol.jade.provider.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum HopperLockProvider implements StreamServerDataProvider<BlockAccessor, Boolean> {
    INSTANCE;

    private static final ResourceLocation MC_HOPPER_LOCK = JadeProtocol.mc_id("hopper_lock");

    @Override
    public Boolean streamData(@NotNull BlockAccessor accessor) {
        return !accessor.getBlockState().getValue(BlockStateProperties.ENABLED);
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Boolean> streamCodec() {
        return ByteBufCodecs.BOOL.cast();
    }

    @Override
    public ResourceLocation getUid() {
        return MC_HOPPER_LOCK;
    }

    @Override
    public int getDefaultPriority() {
        return BlockNameProvider.INSTANCE.getDefaultPriority() + 10;
    }
}