package org.dreeam.leaf.async;

import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.dreeam.leaf.util.queue.MpmcQueue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@NullMarked
public final class ThreadPool implements Executor {
    private static final Logger LOGGER = LogManager.getLogger("Leaf");
    private static final long PARK_NANOS = 200_000L; // 0.2ms

    private volatile boolean shutdown = false;
    private final Thread[] threads;
    private final MpmcQueue<Runnable> channel;
    private final MpmcQueue<Thread> parkChannel;

    public ThreadPool(int numThreads, final int queue, final String prefix, final int priority) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException();
        }
        numThreads = numThreads + 1;
        this.threads = new Thread[numThreads];
        this.channel = new MpmcQueue<>(queue);
        this.parkChannel = new MpmcQueue<>(numThreads);
        this.threads[0] = Thread.ofPlatform()
            .uncaughtExceptionHandler(Util::onThreadException)
            .daemon(false)
            .priority(priority + 1)
            .name(prefix + " Dispatcher")
            .start(new Dispatcher(this));
        for (int i = 1; i < numThreads; i++) {
            threads[i] = Thread.ofPlatform()
                .uncaughtExceptionHandler(Util::onThreadException)
                .daemon(false)
                .priority(priority)
                .name(prefix + " Worker - " + i)
                .start(new Worker(this));
        }
    }

    @Override
    public void execute(Runnable task) {
        if (shutdown || !channel.send(task)) {
            task.run();
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public <V> FutureTask<V> submit(Runnable task, @Nullable V result) {
        final FutureTask<V> t = new FutureTask<>(Executors.callable(task, result));
        execute(t);
        return t;
    }

    public <V> FutureTask<V> submit(Callable<V> task) {
        final FutureTask<V> t = new FutureTask<>(task);
        execute(t);
        return t;
    }

    public void shutdown() {
        shutdown = true;
        for (final Thread thread : threads) {
            LockSupport.unpark(thread);
        }
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        final long nanos = unit.toNanos(timeout);
        final long startTime = System.nanoTime();

        boolean flag = true;
        for (final Thread worker : threads) {
            if (nanos <= 0L) {
                worker.join();
                continue;
            }
            final long remaining = startTime + nanos - System.nanoTime();
            if (remaining <= 0L) {
                flag = false;
                break;
            } else {
                worker.join(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                if (worker.isAlive()) {
                    flag = false;
                    break;
                }
            }
        }
        Runnable task;
        while ((task = channel.recv()) != null) {
            task.run();
        }
        return flag;
    }

    public int workerCount() {
        return threads.length - 1;
    }

    private record Worker(ThreadPool executor) implements Runnable {
        @Override
        public void run() {
            final MpmcQueue<Runnable> channel = executor.channel;
            final MpmcQueue<Thread> park = executor.parkChannel;
            while (true) {
                final Runnable task = channel.recv();
                if (task != null) {
                    try {
                        task.run();
                    } catch (final Throwable e) {
                        LOGGER.error("Task {} generated an exception: {}", task, Thread.currentThread().getName(), e);
                    }
                } else if (executor.shutdown) {
                    break;
                } else if (park.send(Thread.currentThread())) {
                    LockSupport.park();
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    Thread.yield();
                }
            }
        }
    }

    private record Dispatcher(ThreadPool executor) implements Runnable {
        @Override
        public void run() {
            final int threads = executor.threads.length - 1;
            final MpmcQueue<Runnable> channel = executor.channel;
            final MpmcQueue<Thread> park = executor.parkChannel;
            int backoff = 0;
            while (true) {
                final int len = channel.length();
                if (len != 0 && threads - park.length() < len) {
                    backoff = 0;
                    final Thread thread = park.recv();
                    if (thread != null) {
                        LockSupport.unpark(thread);
                    }
                } else if (executor.shutdown) {
                    break;
                } else if (backoff < 8) {
                    backoff++;
                    Thread.yield();
                } else {
                    LockSupport.parkNanos(PARK_NANOS);
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            Thread left;
            while ((left = park.recv()) != null) {
                LockSupport.unpark(left);
            }
        }
    }
}
