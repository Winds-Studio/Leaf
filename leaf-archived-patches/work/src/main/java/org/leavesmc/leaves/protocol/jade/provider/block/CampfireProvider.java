package org.leavesmc.leaves.protocol.jade.provider.block;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;
import org.leavesmc.leaves.protocol.jade.provider.IServerExtensionProvider;
import org.leavesmc.leaves.protocol.jade.util.ViewGroup;

import java.util.List;

public enum CampfireProvider implements IServerExtensionProvider<ItemStack> {
    INSTANCE;

    private static final MapCodec<Integer> COOKING_TIME_CODEC = Codec.INT.fieldOf("jade:cooking");
    private static final ResourceLocation MC_CAMPFIRE = JadeProtocol.mc_id("campfire");

    @Override
    public @Nullable @Unmodifiable List<ViewGroup<ItemStack>> getGroups(@NotNull Accessor<?> request) {
        if (request.getTarget() instanceof CampfireBlockEntity campfire) {
            List<ItemStack> list = Lists.newArrayList();
            for (int i = 0; i < campfire.cookingTime.length; i++) {
                ItemStack stack = campfire.getItems().get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                stack = stack.copy();

                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .update(NbtOps.INSTANCE, COOKING_TIME_CODEC, campfire.cookingTime[i] - campfire.cookingProgress[i])
                    .getOrThrow();
                stack.set(DataComponents.CUSTOM_DATA, customData);

                list.add(stack);
            }
            return List.of(new ViewGroup<>(list));
        }
        return null;
    }

    @Override
    public ResourceLocation getUid() {
        return MC_CAMPFIRE;
    }
}
