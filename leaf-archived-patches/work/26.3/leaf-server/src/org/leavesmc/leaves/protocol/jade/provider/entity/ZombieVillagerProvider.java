package org.leavesmc.leaves.protocol.jade.provider.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum ZombieVillagerProvider implements StreamServerDataProvider<EntityAccessor, Integer> {
    INSTANCE;

    private static final Identifier MC_ZOMBIE_VILLAGER = JadeProtocol.mc_id("zombie_villager");

    @Override
    public @Nullable Integer streamData(@NotNull EntityAccessor accessor) {
        int time = ((ZombieVillager) accessor.getEntity()).villagerConversionTime;
        return time > 0 ? time : null;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, Integer> streamCodec() {
        return ByteBufCodecs.VAR_INT.cast();
    }

    @Override
    public Identifier getUid() {
        return MC_ZOMBIE_VILLAGER;
    }
}
