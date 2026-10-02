package org.leavesmc.leaves.protocol.jade.accessor;

import com.google.common.base.Suppliers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.util.CommonUtil;

import java.util.function.Supplier;

public class EntityAccessor extends Accessor<EntityHitResult> {

    private final Supplier<Entity> entity;

    public EntityAccessor(Builder builder) {
        super(builder.level, builder.player, builder.hit);
        entity = builder.entity;
    }

    public Entity getEntity() {
        return CommonUtil.wrapPartEntityParent(getRawEntity());
    }

    public Entity getRawEntity() {
        return entity.get();
    }

    @NotNull
    @Override
    public Object getTarget() {
        return getEntity();
    }

    public static class Builder {
        private ServerLevel level;
        private Player player;
        private Supplier<EntityHitResult> hit;
        private Supplier<Entity> entity;

        public Builder level(ServerLevel level) {
            this.level = level;
            return this;
        }

        public Builder player(Player player) {
            this.player = player;
            return this;
        }

        public Builder hit(Supplier<EntityHitResult> hit) {
            this.hit = hit;
            return this;
        }

        public Builder entity(Supplier<Entity> entity) {
            this.entity = entity;
            return this;
        }

        public Builder from(EntityAccessor accessor) {
            level = accessor.getLevel();
            player = accessor.getPlayer();
            hit = accessor::getHitResult;
            entity = accessor::getEntity;
            return this;
        }

        public EntityAccessor build() {
            return new EntityAccessor(this);
        }
    }

    public record SyncData(boolean showDetails, int id, int partIndex, Vec3 hitVec, CompoundTag data) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SyncData::showDetails,
            ByteBufCodecs.VAR_INT,
            SyncData::id,
            ByteBufCodecs.VAR_INT,
            SyncData::partIndex,
            ByteBufCodecs.VECTOR3F.map(Vec3::new, Vec3::toVector3f),
            SyncData::hitVec,
            ByteBufCodecs.COMPOUND_TAG,
            SyncData::data,
            SyncData::new
        );

        public EntityAccessor unpack(ServerPlayer player) {
            Supplier<Entity> entity = Suppliers.memoize(() -> CommonUtil.getPartEntity(player.level().getEntity(id), partIndex));
            return new Builder()
                .level(player.level())
                .player(player)
                .entity(entity)
                .hit(Suppliers.memoize(() -> new EntityHitResult(entity.get(), hitVec)))
                .build();
        }
    }
}
