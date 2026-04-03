package org.leavesmc.leaves.protocol.jade.provider.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;
import org.leavesmc.leaves.protocol.jade.util.CommonUtil;

import java.util.UUID;

public enum AnimalOwnerProvider implements StreamServerDataProvider<EntityAccessor, String> {
    INSTANCE;

    private static final ResourceLocation MC_ANIMAL_OWNER = JadeProtocol.mc_id("animal_owner");

    public static UUID getOwnerUUID(Entity entity) {
        if (entity instanceof OwnableEntity ownableEntity) {
            EntityReference<LivingEntity> reference = ownableEntity.getOwnerReference();
            if (reference != null) {
                return reference.getUUID();
            }
        }
        return null;
    }

    @Override
    public String streamData(@NotNull EntityAccessor accessor) {
        return CommonUtil.getLastKnownUsername(getOwnerUUID(accessor.getEntity()));
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, String> streamCodec() {
        return ByteBufCodecs.STRING_UTF8.cast();
    }

    @Override
    public ResourceLocation getUid() {
        return MC_ANIMAL_OWNER;
    }
}