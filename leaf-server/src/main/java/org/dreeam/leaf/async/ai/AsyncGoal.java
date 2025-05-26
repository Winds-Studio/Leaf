package org.dreeam.leaf.async.ai;

public interface AsyncGoal {
    default boolean tickAsync() {
        return true;
    }
}
