package org.dreeam.leaf.async;

import net.minecraft.Util;
import org.dreeam.leaf.util.queue.MpmcQueue;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.LockSupport;

public final class FixedThreadExecutor {
    private final Thread[] workers;
    private final MpmcQueue<Runnable> channel;
    private static volatile boolean SHUTDOWN = false;

    public FixedThreadExecutor(int numThreads, int queue, String prefix) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException();
        }
        this.workers = new Thread[numThreads];
        this.channel = new MpmcQueue<>(Runnable.class, queue);
        for (int i = 0; i < numThreads; i++) {
            workers[i] = Thread.ofPlatform()
                .uncaughtExceptionHandler(Util::onThreadException)
                .daemon(false)
                .priority(Thread.NORM_PRIORITY)
                .name(prefix + " - " + i)
                .start(new Worker(channel));
        }
    }

    public <T> FutureTask<T> submitOrRun(Callable<T> task) {
        if (SHUTDOWN) {
            throw new IllegalStateException();
        }

        final FutureTask<T> t = new FutureTask<>(task);
        if (!channel.send(t)) {
            t.run();
        }
        return t;
    }

    public void unpack() {
        int size = Math.min(Math.max(1, channel.length()), workers.length);
        for (int i = 0; i < size; i++) {
            LockSupport.unpark(workers[i]);
        }
    }

    public void shutdown() {
        SHUTDOWN = true;
        for (final Thread worker : workers) {
            LockSupport.unpark(worker);
        }
    }

    public void join(long timeoutMillis) throws InterruptedException {
        final long startTime = System.currentTimeMillis();

        for (final Thread worker : workers) {
            final long remaining = timeoutMillis - System.currentTimeMillis() + startTime;
            if (remaining <= 0) {
                return;
            }
            worker.join(remaining);
            if (worker.isAlive()) {
                return;
            }
        }
    }

    private record Worker(MpmcQueue<Runnable> channel) implements Runnable {
        @Override
        public void run() {
            while (!SHUTDOWN) {
                final Runnable task = channel.recv();
                if (task != null) {
                    task.run();
                } else if (!SHUTDOWN) {
                    LockSupport.park();
                }
            }
        }
    }
}
