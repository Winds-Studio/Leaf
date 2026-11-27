package org.dreeam.leaf.async;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.papermc.paper.connection.PaperConfigurationTask;
import io.papermc.paper.threadedregions.scheduler.FoliaAsyncScheduler;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Util;
import org.dreeam.leaf.config.modules.opt.VirtualThreadSupport;
import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorFactory {

    public static ExecutorService buildChatExecutor() {
        // Leaf start - Virtual thread support for chat executor
        if (VirtualThreadSupport.asyncChatExecutor) {
            return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                    .name("Async Chat Thread - #", 0)
                    .uncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(MinecraftServer.LOGGER))
                    .factory()
            );
        }
        // Leaf end - Virtual thread support for chat executor
        return Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("Async Chat Thread - #%d")
                .setThreadFactory(Executors.defaultThreadFactory())
                .setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(MinecraftServer.LOGGER))
                .build()
        ); // Paper
    }

    public static ExecutorService buildDownloadPoolExecutor() {
        // Leaf start - Virtual thread support for download pool
        if (VirtualThreadSupport.downloadPool) {
            return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                    .name("Download-", 0)
                    .uncaughtExceptionHandler((Thread thread, Throwable throwable) -> Util.LOGGER.error("Uncaught exception in thread {}", thread.getName(), throwable))
                    .factory()
            );
        }
        // Leaf end - Virtual thread support for download pool
        return Executors.newFixedThreadPool(4, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(@NonNull Runnable run) {
                Thread ret = new Thread(run);
                ret.setDaemon(true);
                ret.setName("Download-" + this.count.getAndIncrement());
                ret.setUncaughtExceptionHandler((Thread thread, Throwable throwable) -> Util.LOGGER.error("Uncaught exception in thread {}", thread.getName(), throwable));
                return ret;
            }
        }); // Paper - Limit download pool size
    }

    public static ExecutorService buildBukkitAsyncSchedulerExecutor() {
        // Leaf start - Virtual thread support for bukkit async scheduler
        if (VirtualThreadSupport.bukkitAsyncScheduler) {
            return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                    .name("Craft Scheduler Thread - ", 0)
                    .uncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LoggerFactory.getLogger("CraftAsyncScheduler")))
                    .factory()
            );
        }
        // Leaf end - Virtual thread support for bukkit async scheduler
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            4, Integer.MAX_VALUE, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("Craft Scheduler Thread - %1$d").build()
        );

        executor.allowCoreThreadTimeOut(true);
        executor.prestartAllCoreThreads();
        return executor;
    }

    public static ExecutorService buildFoliaAsyncSchedulerExecutor() {
        // Leaf start - Virtual thread support for folia async scheduler
        if (VirtualThreadSupport.foliaAsyncScheduler) {
            return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                    .name("Folia Async Scheduler Thread #", 0)
                    .uncaughtExceptionHandler((final Thread thread, final Throwable thr) -> FoliaAsyncScheduler.LOGGER.error("Uncaught exception in thread: {}", thread.getName(), thr))
                    .factory()
            );
        }
        // Leaf end - Virtual thread support for folia async scheduler
        return new ThreadPoolExecutor(Math.max(4, Runtime.getRuntime().availableProcessors() / 2), Integer.MAX_VALUE,
            30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger idGenerator = new AtomicInteger();

                @Override
                public Thread newThread(final @NonNull Runnable run) {
                    final Thread ret = new Thread(run);

                    ret.setName("Folia Async Scheduler Thread #" + this.idGenerator.getAndIncrement());
                    ret.setPriority(Thread.NORM_PRIORITY - 1);
                    ret.setUncaughtExceptionHandler((final Thread thread, final Throwable thr) -> FoliaAsyncScheduler.LOGGER.error("Uncaught exception in thread: {}", thread.getName(), thr));

                    return ret;
                }
            }
        );
    }
}
