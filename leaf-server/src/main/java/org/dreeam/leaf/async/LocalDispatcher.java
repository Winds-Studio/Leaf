package org.dreeam.leaf.async;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@NullMarked
public final class LocalDispatcher implements Executor, Runnable {
    private final ConcurrentLinkedQueue<Runnable> queue;
    private final AtomicReference<@Nullable Thread> runner;
    private final AtomicBoolean guard = new AtomicBoolean(false);

    public LocalDispatcher() {
        this.queue = new ConcurrentLinkedQueue<>();
        this.runner = new AtomicReference<>();
    }

    @Override
    public void execute(Runnable task) {
        queue.offer(task);
        if (guard.compareAndSet(false, true)) {
            GlobalDispatcher.INSTANCE.execute(this);
        }
    }

    public boolean isRunning() {
        return runner.get() != null;
    }

    public boolean isSameThread() {
        return Thread.currentThread() == runner.get();
    }

    @Override
    public void run() {
        if (!runner.compareAndSet(null, Thread.currentThread())) {
            return;
        }
        try {
            Runnable task;
            while ((task = queue.poll()) != null) {
                task.run();
            }
        } finally {
            runner.set(null);
            guard.set(false);
            if (!queue.isEmpty() && guard.compareAndSet(false, true)) {
                GlobalDispatcher.INSTANCE.execute(this);
            }
        }
    }
}
