package org.dreeam.leaf.async.ai;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface VWaker {
    @Nullable Object wake(ServerLevel world);
}
