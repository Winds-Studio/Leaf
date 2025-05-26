package org.dreeam.leaf.async.ai;

import org.jetbrains.annotations.Nullable;

public class Waker {

    @Nullable
    public volatile VWaker wake = null;
    @Nullable
    public volatile Object result = null;
    public boolean state = true;

    public final @Nullable Object result() {
        if (state) {
            return null;
        }
        Object result = this.result;
        this.result = null;
        return result;
    }

    final void wake() {
        final var wake = this.wake;
        if (wake != null) {
            try {
                this.result = wake.wake();
            } catch (Exception e) {
                AsyncGoalExecutor.LOGGER.error("Exception while wake", e);
            }
            this.wake = null;
        }
    }
}
