package org.leavesmc.leaves.protocol.jade.provider.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.StreamServerDataProvider;

public enum PetArmorProvider implements StreamServerDataProvider<EntityAccessor, ItemStack> {
    INSTANCE;

    private static final ResourceLocation MC_PET_ARMOR = JadeProtocol.mc_id("pet_armor");

    @Nullable
    @Override
    public ItemStack streamData(@NotNull EntityAccessor accessor) {
        ItemStack armor = ((Mob) accessor.getEntity()).getBodyArmorItem();
        return armor.isEmpty() ? null : armor;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ItemStack> streamCodec() {
        return ItemStack.OPTIONAL_STREAM_CODEC;
    }

    @Override
    public ResourceLocation getUid() {
        return MC_PET_ARMOR;
    }
}
