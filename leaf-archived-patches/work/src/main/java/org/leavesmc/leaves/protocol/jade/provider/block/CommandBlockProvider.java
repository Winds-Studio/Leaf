package org.leavesmc.leaves.protocol.jade.provider.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum CommandBlockProvider implements StreamServerDataProvider<BlockAccessor, String> {
    INSTANCE;

    private static final ResourceLocation MC_COMMAND_BLOCK = JadeProtocol.mc_id("command_block");

    @Nullable
    public String streamData(@NotNull BlockAccessor accessor) {
        if (!accessor.getPlayer().canUseGameMasterBlocks()) {
            return null;
        }
        String command = ((CommandBlockEntity) accessor.getBlockEntity()).getCommandBlock().getCommand();
        if (command.length() > 40) {
            command = command.substring(0, 37) + "...";
        }
        return command;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, String> streamCodec() {
        return ByteBufCodecs.STRING_UTF8.cast();
    }

    @Override
    public ResourceLocation getUid() {
        return MC_COMMAND_BLOCK;
    }
}
