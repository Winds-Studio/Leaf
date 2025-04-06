package org.dreeam.leaf.async;

import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;

import java.util.Optional;
import java.util.concurrent.*;

public class AsyncPlayerDataSaving {

    public static final ExecutorService IO_POOL = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setPriority(Thread.NORM_PRIORITY - 2)
            .setNameFormat("Leaf IO Thread")
            .setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER))
            .build(),
        new ThreadPoolExecutor.DiscardPolicy()
    );

    private AsyncPlayerDataSaving() {
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
