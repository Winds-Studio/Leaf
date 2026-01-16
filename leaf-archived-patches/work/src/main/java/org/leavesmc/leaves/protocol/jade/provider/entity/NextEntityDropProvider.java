package org.leavesmc.leaves.protocol.jade.provider.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.provider.IServerDataProvider;

public enum NextEntityDropProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation MC_NEXT_ENTITY_DROP = JadeProtocol.mc_id("next_entity_drop");

    @Override
    public void appendServerData(CompoundTag tag, @NotNull EntityAccessor accessor) {
        int max = 24000 * 2;
        if (accessor.getEntity() instanceof Chicken chicken) {
            if (!chicken.isBaby() && chicken.eggTime < max) {
                tag.putInt("NextEggIn", chicken.eggTime);
            }
        } else if (accessor.getEntity() instanceof Armadillo armadillo) {
            if (!armadillo.isBaby() && armadillo.scuteTime < max) {
                tag.putInt("NextScuteIn", armadillo.scuteTime);
            }
        } else if (accessor.getEntity() instanceof Sniffer sniffer) {
            long time = sniffer.getBrain().getTimeUntilExpiry(MemoryModuleType.SNIFF_COOLDOWN);
            if (time > 0 && time < max) {
                tag.putInt("NextSniffIn", (int) time);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return MC_NEXT_ENTITY_DROP;
    }
}
