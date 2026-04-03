package org.leavesmc.leaves.protocol.jade.provider;

import net.minecraft.resources.ResourceLocation;

public interface IJadeProvider {

    ResourceLocation getUid();

    default int getDefaultPriority() {
        return 0;
    }
}
