package org.leavesmc.leaves.protocol.jade.provider;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.LockCode;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.util.CommonUtil;
import org.leavesmc.leaves.protocol.jade.util.ItemCollector;
import org.leavesmc.leaves.protocol.jade.util.ViewGroup;

import java.util.List;
import java.util.Map;

public abstract class ItemStorageProvider<T extends Accessor<?>> implements IServerDataProvider<T> {

    private static final StreamCodec<RegistryFriendlyByteBuf, Map.Entry<ResourceLocation, List<ViewGroup<ItemStack>>>> STREAM_CODEC = ViewGroup.listCodec(ItemStack.OPTIONAL_STREAM_CODEC);

    private static final ResourceLocation UNIVERSAL_ITEM_STORAGE = JadeProtocol.mc_id("item_storage");

    public static ForBlock getBlock() {
        return ForBlock.INSTANCE;
    }

    public static ForEntity getEntity() {
        return ForEntity.INSTANCE;
    }

    public static void putData(CompoundTag tag, @NotNull Accessor<?> accessor) {
        Object target = accessor.getTarget();
        Player player = accessor.getPlayer();
        Map.Entry<ResourceLocation, List<ViewGroup<ItemStack>>> entry = CommonUtil.getServerExtensionData(accessor, JadeProtocol.itemStorageProviders);
        if (entry != null) {
            List<ViewGroup<ItemStack>> groups = entry.getValue();
            for (ViewGroup<ItemStack> group : groups) {
                if (group.views.size() > ItemCollector.MAX_SIZE) {
                    group.views = group.views.subList(0, ItemCollector.MAX_SIZE);
                }
            }
            tag.put(UNIVERSAL_ITEM_STORAGE.toString(), accessor.encodeAsNbt(STREAM_CODEC, entry));
            return;
        }
        if (target instanceof RandomizableContainer containerEntity && containerEntity.getLootTable() != null) {
            tag.putBoolean("Loot", true);
        } else if (!player.isCreative() && !player.isSpectator() && target instanceof BaseContainerBlockEntity te) {
            if (te.lockKey != LockCode.NO_LOCK) {
                tag.putBoolean("Locked", true);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UNIVERSAL_ITEM_STORAGE;
    }

    @Override
    public void appendServerData(CompoundTag tag, @NotNull T accessor) {
        if (accessor.getTarget() instanceof AbstractFurnaceBlockEntity) {
            return;
        }
        putData(tag, accessor);
    }

    @Override
    public int getDefaultPriority() {
        return 1000;
    }

    public static class ForBlock extends ItemStorageProvider<BlockAccessor> {
        private static final ForBlock INSTANCE = new ForBlock();
    }

    public static class ForEntity extends ItemStorageProvider<EntityAccessor> {
        private static final ForEntity INSTANCE = new ForEntity();
    }
}