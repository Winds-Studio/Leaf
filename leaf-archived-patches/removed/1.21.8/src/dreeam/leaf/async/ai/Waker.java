package org.dreeam.leaf.async.ai;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class Waker {

    @Nullable
    public volatile VWaker wake = null;
    @Nullable
    public volatile Object result = null;
    private volatile boolean cancel = false;
    public boolean state = true;

    public final @Nullable Object result() {
        Object result = this.result;
        this.result = null;
        return result;
    }

    public final void cancel() {
        this.cancel = true;
        this.wake = null;
        this.result = null;
    }

    final void wake(ServerLevel world) {
        final VWaker wake = this.wake;
        if (wake != null) {
            try {
                this.result = wake.wake(world);
            } catch (Exception e) {
                AsyncGoalExecutor.LOGGER.error("Exception while wake", e);
            }
            this.wake = null;
            if (this.cancel) {
                this.result = null;
            }
        }
    }
}
