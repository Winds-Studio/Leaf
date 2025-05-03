package org.dreeam.leaf.async.ai;

import org.jetbrains.annotations.Nullable;

public class Waker {

    @Nullable
    public volatile Runnable wake = null;
    @Nullable
    public volatile Object result = null;
    public volatile boolean state = true;

    public final @Nullable Object result() {
        Object result = this.result;
        this.result = null;
        return result;
    }
}
