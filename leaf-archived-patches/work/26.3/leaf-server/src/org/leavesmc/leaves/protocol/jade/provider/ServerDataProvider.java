package org.leavesmc.leaves.protocol.jade.provider;

import net.minecraft.nbt.CompoundTag;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;

public interface ServerDataProvider<T extends Accessor<?>> extends JadeProvider {
    void appendServerData(CompoundTag data, T accessor);
}
