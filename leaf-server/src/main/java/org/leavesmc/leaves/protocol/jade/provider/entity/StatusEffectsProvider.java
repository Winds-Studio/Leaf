package org.leavesmc.leaves.protocol.jade.provider.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

import java.util.List;

public enum StatusEffectsProvider implements StreamServerDataProvider<EntityAccessor, List<MobEffectInstance>> {
    INSTANCE;


    private static final StreamCodec<RegistryFriendlyByteBuf, List<MobEffectInstance>> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf, MobEffectInstance>list()
        .apply(MobEffectInstance.STREAM_CODEC);
    private static final ResourceLocation MC_POTION_EFFECTS = JadeProtocol.mc_id("potion_effects");

    @Override
    @Nullable
    public List<MobEffectInstance> streamData(@NotNull EntityAccessor accessor) {
        List<MobEffectInstance> effects = ((LivingEntity) accessor.getEntity()).getActiveEffects()
            .stream()
            .filter(MobEffectInstance::isVisible)
            .toList();
        return effects.isEmpty() ? null : effects;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, List<MobEffectInstance>> streamCodec() {
        return STREAM_CODEC;
    }


    @Override
    public ResourceLocation getUid() {
        return MC_POTION_EFFECTS;
    }
}
