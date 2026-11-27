package org.leavesmc.leaves.protocol.jade.accessor;

import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Class to get information of block target and context.
 */
public class BlockAccessor extends Accessor<BlockHitResult> {

    private final BlockState blockState;
    @Nullable
    private final Supplier<BlockEntity> blockEntity;

    private BlockAccessor(Builder builder) {
        super(builder.level, builder.player, Suppliers.ofInstance(builder.hit));
        blockState = builder.blockState;
        blockEntity = builder.blockEntity;
    }

    public Block getBlock() {
        return getBlockState().getBlock();
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public BlockEntity getBlockEntity() {
        return blockEntity == null ? null : blockEntity.get();
    }

    public BlockPos getPosition() {
        return getHitResult().getBlockPos();
    }

    @Nullable
    @Override
    public Object getTarget() {
        return getBlockEntity();
    }

    public static class Builder {
        private ServerLevel level;
        private Player player;
        private BlockHitResult hit;
        private BlockState blockState = Blocks.AIR.defaultBlockState();
        private Supplier<BlockEntity> blockEntity;

        public Builder level(ServerLevel level) {
            this.level = level;
            return this;
        }

        public Builder player(Player player) {
            this.player = player;
            return this;
        }

        public Builder hit(BlockHitResult hit) {
            this.hit = hit;
            return this;
        }

        public Builder blockState(BlockState blockState) {
            this.blockState = blockState;
            return this;
        }

        public Builder blockEntity(Supplier<BlockEntity> blockEntity) {
            this.blockEntity = blockEntity;
            return this;
        }

        public Builder from(BlockAccessor accessor) {
            level = accessor.getLevel();
            player = accessor.getPlayer();
            hit = accessor.getHitResult();
            blockEntity = accessor::getBlockEntity;
            blockState = accessor.getBlockState();
            return this;
        }

        public BlockAccessor build() {
            return new BlockAccessor(this);
        }
    }

    public record SyncData(boolean showDetails, BlockHitResult hit, ItemStack serversideRep, CompoundTag data) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SyncData::showDetails,
            StreamCodec.of(FriendlyByteBuf::writeBlockHitResult, FriendlyByteBuf::readBlockHitResult),
            SyncData::hit,
            ItemStack.OPTIONAL_STREAM_CODEC,
            SyncData::serversideRep,
            ByteBufCodecs.COMPOUND_TAG,
            SyncData::data,
            SyncData::new
        );

        public BlockAccessor unpack(ServerPlayer player) {
            Supplier<BlockEntity> blockEntity = null;
            BlockState blockState = player.level().getBlockState(hit.getBlockPos());
            if (blockState.hasBlockEntity()) {
                blockEntity = Suppliers.memoize(() -> player.level().getBlockEntity(hit.getBlockPos()));
            }
            return new Builder()
                .level(player.level())
                .player(player)
                .hit(hit)
                .blockState(blockState)
                .blockEntity(blockEntity)
                .build();
        }
    }
}
