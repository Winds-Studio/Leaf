package org.dreeam.leaf.async;

import net.minecraft.util.Util;
import org.dreeam.leaf.util.queue.MpmcQueue;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.LockSupport;

public final class ThreadPool {
    private final Thread[] threads;
    private final MpmcQueue<Runnable> channel;
    private volatile boolean shutdown = false;

    public ThreadPool(final int numThreads, final int queue, final String prefix) {
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
                .start(new Worker(this));
        }
    }

    public <T> FutureTask<T> submitOrRun(final Callable<T> task) {
        if (shutdown) {
            throw new IllegalStateException();
        }

        final FutureTask<T> t = new FutureTask<>(task);
        if (!channel.send(t)) {
            t.run();
        }
        return t;
    }

    public void unpark() {
        final int len = Math.clamp(channel.length(), 1, threads.length);
        for (int i = 0; i < len; i++) {
            LockSupport.unpark(threads[i]);
        }
    }

    public void shutdown() {
        shutdown = true;
        for (final Thread thread : threads) {
            LockSupport.unpark(thread);
        }
    }

    public void join(final long timeoutMillis) throws InterruptedException {
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

    public int threadCount() {
        return this.threads.length;
    }

    private record Worker(ThreadPool executor) implements Runnable {
        @Override
        public void run() {
            MpmcQueue<Runnable> channel = executor.channel;
            while (true) {
                final Runnable task = channel.recv();
                if (task != null) {
                    task.run();
                } else if (executor.shutdown) {
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
