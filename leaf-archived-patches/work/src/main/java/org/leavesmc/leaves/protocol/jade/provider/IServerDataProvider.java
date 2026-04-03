package org.leavesmc.leaves.protocol.jade.provider;

import net.minecraft.nbt.CompoundTag;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;

public interface IServerDataProvider<T extends Accessor<?>> extends IJadeProvider {
    void appendServerData(CompoundTag data, T accessor);
}
