package org.dreeam.leaf.async;

import net.minecraft.util.Util;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncPlayerDataSaving {

    public static ExecutorService IO_POOL = null;

    private AsyncPlayerDataSaving() {
    }

    public static void init() {
        if (IO_POOL == null) {
            IO_POOL = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new com.google.common.util.concurrent.ThreadFactoryBuilder()
                    .setPriority(Thread.NORM_PRIORITY - 2)
                    .setNameFormat("Leaf IO Thread")
                    .setUncaughtExceptionHandler(Util::onThreadException)
                    .build(),
                new ThreadPoolExecutor.DiscardPolicy()
            );
        } else {
            // Temp no-op
            //throw new IllegalStateException();
        }
    }

    public static Optional<Future<?>> submit(Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            runnable.run();
            return Optional.empty();
        } else {
            return Optional.of(IO_POOL.submit(runnable));
        }
    }
}
