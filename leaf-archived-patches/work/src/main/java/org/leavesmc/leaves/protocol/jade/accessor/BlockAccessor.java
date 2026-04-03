package org.leavesmc.leaves.protocol.jade.accessor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

public interface BlockAccessor extends Accessor<BlockHitResult> {

    Block getBlock();

    BlockState getBlockState();

    BlockEntity getBlockEntity();

    BlockPos getPosition();

    @ApiStatus.NonExtendable
    interface Builder {
        Builder level(ServerLevel level);

        Builder player(Player player);

        Builder hit(BlockHitResult hit);

        Builder blockState(BlockState state);

        default Builder blockEntity(BlockEntity blockEntity) {
            return blockEntity(() -> blockEntity);
        }

        Builder blockEntity(Supplier<BlockEntity> blockEntity);

        Builder from(BlockAccessor accessor);

        BlockAccessor build();
    }
}
