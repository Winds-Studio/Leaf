package org.leavesmc.leaves.protocol.jade.provider.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

import java.util.UUID;

public enum AnimalOwnerProvider implements StreamServerDataProvider<EntityAccessor, Component> {
    INSTANCE;

    private static final Identifier MC_ANIMAL_OWNER = JadeProtocol.mc_id("animal_owner");

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
    public Component streamData(@NotNull EntityAccessor accessor) {
        ServerLevel level = accessor.getLevel();
        UUID uuid = getOwnerUUID(accessor.getEntity());
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            return entity.getName();
        }
        String name = lookupPlayerName(uuid, level.getServer().services());
        return name == null ? null : Component.literal(name);
    }

    @Nullable
    public static String lookupPlayerName(@Nullable UUID uuid, Services services) {
        if (uuid == null) {
            return null;
        }
        String name = services.nameToIdCache().get(uuid).map(NameAndId::name).orElse(null);
        if (name != null) {
            return name;
        }
        GameProfile profile = services.profileResolver().fetchById(uuid).orElse(null);
        return profile == null ? null : profile.name();
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
        return ComponentSerialization.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return MC_ANIMAL_OWNER;
    }
}
