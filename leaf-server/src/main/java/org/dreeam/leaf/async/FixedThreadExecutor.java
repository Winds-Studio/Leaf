package org.dreeam.leaf.async;

import net.minecraft.Util;
import org.dreeam.leaf.util.queue.MpmcQueue;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.LockSupport;

public final class FixedThreadExecutor {
    private final Thread[] threads;
    public final MpmcQueue<Runnable> channel;
    private static volatile boolean SHUTDOWN = false;

    public FixedThreadExecutor(int numThreads, int queue, String prefix) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException();
        }
        this.threads = new Thread[numThreads];
        this.channel = new MpmcQueue<>(Runnable.class, queue);
        for (int i = 0; i < numThreads; i++) {
            threads[i] = Thread.ofPlatform()
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
        final int len = Math.clamp(channel.length(), 1, threads.length);
        for (int i = 0; i < len; i++) {
            LockSupport.unpark(threads[i]);
        }
    }

    public void shutdown() {
        SHUTDOWN = true;
        for (Thread thread : threads) {
            LockSupport.unpark(thread);
        }
    }

    public void join(long timeoutMillis) throws InterruptedException {
        final long startTime = System.currentTimeMillis();

        for (final Thread worker : threads) {
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
            while (true) {
                final Runnable task = channel.recv();
                if (task != null) {
                    task.run();
                } else if (SHUTDOWN) {
                    break;
                } else {
                    Thread.yield();
                    if (channel.isEmpty()) {
                        LockSupport.park();
                        if (Thread.interrupted()) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
