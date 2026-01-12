package org.dreeam.leaf.async;

import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncPlayerDataSaving {

    private static final Logger LOGGER = LogManager.getLogger("Leaf");
    private static final int QUEUE_CAPACITY = 256;

    public static ExecutorService IO_POOL = null;
    public static ExecutorService LEVEL_IO_POOL = null;

    private AsyncPlayerDataSaving() {
    }

    public static void init() {
        getOrCreateIoPool();
        getOrCreateLevelPool();
    }

    public static Optional<Future<?>> submit(Runnable runnable) {
        return submitPlayer(runnable);
    }

    public static Optional<Future<?>> submitPlayer(Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            runnable.run();
            return Optional.empty();
        }
        return submitTo(getOrCreateIoPool(), runnable, "player IO");
    }

    public static Optional<Future<?>> submitLevel(Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            runnable.run();
            return Optional.empty();
        }
        return submitTo(getOrCreateLevelPool(), runnable, "level IO");
    }

    private static Optional<Future<?>> submitTo(ExecutorService executor, Runnable runnable, String label) {
        if (executor.isShutdown() || executor.isTerminated()) {
            LOGGER.warn("Async {} executor is shutdown; running task on caller thread.", label);
            runnable.run();
            return Optional.empty();
        }
        try {
            return Optional.of(executor.submit(runnable));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            LOGGER.warn("Async {} executor rejected task; running task on caller thread.", label, exception);
            runnable.run();
            return Optional.empty();
        }
    }

    private static synchronized ExecutorService getOrCreateIoPool() {
        if (IO_POOL == null || IO_POOL.isShutdown() || IO_POOL.isTerminated()) {
            IO_POOL = createExecutor("Leaf Player IO Thread %d", "player IO");
        }
        return IO_POOL;
    }

    private static synchronized ExecutorService getOrCreateLevelPool() {
        if (LEVEL_IO_POOL == null || LEVEL_IO_POOL.isShutdown() || LEVEL_IO_POOL.isTerminated()) {
            LEVEL_IO_POOL = createExecutor("Leaf Level IO Thread %d", "level IO");
        }
        return LEVEL_IO_POOL;
    }

    private static ExecutorService createExecutor(String nameFormat, String label) {
        return new ThreadPoolExecutor(
            1,
            1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new com.google.common.util.concurrent.ThreadFactoryBuilder()
                .setPriority(Thread.NORM_PRIORITY - 2)
                .setNameFormat(nameFormat)
                .setUncaughtExceptionHandler(Util::onThreadException)
                .build(),
            (runnable, executor) -> {
                LOGGER.warn("Async {} queue full; running task on caller thread.", label);
                runnable.run();
            }
        );
    }
}
