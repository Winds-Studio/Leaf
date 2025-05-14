package org.dreeam.leaf.async.ai;

import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface VWaker {
    @Nullable Object wake();
}
