package org.dreeam.leaf.async;

import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncPlayerDataSaving {
    public static final ExecutorService IO_POOL = new ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS,
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

    public static void save(Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            runnable.run();
        } else {
            IO_POOL.execute(runnable);
        }
    }
}
