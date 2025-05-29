package org.dreeam.leaf.async.path;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPathfinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * used to handle the scheduling of async path processing
 */
public class AsyncPathProcessor {

    private static final String THREAD_PREFIX = "Leaf Async Pathfinding";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);
    private static long lastWarnMillis = System.currentTimeMillis();
    private static final ThreadPoolExecutor pathProcessingExecutor = new ThreadPoolExecutor(
        1,
        AsyncPathfinding.asyncPathfindingMaxThreads,
        AsyncPathfinding.asyncPathfindingKeepalive, TimeUnit.SECONDS,
        getQueueImpl(),
        new ThreadFactoryBuilder()
            .setNameFormat(THREAD_PREFIX + " Thread - %d")
            .setPriority(Thread.NORM_PRIORITY - 2)
            .build(),
        new RejectedTaskHandler()
    );

    private static class RejectedTaskHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable rejectedTask, ThreadPoolExecutor executor) {
            BlockingQueue<Runnable> workQueue = executor.getQueue();
            if (!executor.isShutdown()) {
                switch (AsyncPathfinding.asyncPathfindingRejectPolicy) {
                    case FLUSH_ALL -> {
                        if (!workQueue.isEmpty()) {
                            List<Runnable> pendingTasks = new ArrayList<>(workQueue.size());

                            workQueue.drainTo(pendingTasks);

                            for (Runnable pendingTask : pendingTasks) {
                                pendingTask.run();
                            }
                        }
                        rejectedTask.run();
                    }
                    case CALLER_RUNS -> rejectedTask.run();
                }
            }

            if (System.currentTimeMillis() - lastWarnMillis > 30000L) {
                LOGGER.warn("Async pathfinding processor is busy! Pathfinding tasks will be treated as policy defined in config. Increasing max-threads in Leaf config may help.");
                lastWarnMillis = System.currentTimeMillis();
            }
        }
    }

    protected static CompletableFuture<Void> queue(@NotNull AsyncPath path) {
        return CompletableFuture.runAsync(path::process, pathProcessingExecutor)
            .orTimeout(60L, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                if (throwable instanceof TimeoutException e) {
                    LOGGER.warn("Async Pathfinding process timed out", e);
                } else LOGGER.warn("Error occurred while processing async path", throwable);
                return null;
            });
    }

    /**
     * takes a possibly unprocessed path, and waits until it is completed
     * the consumer will be immediately invoked if the path is already processed
     * the consumer will always be called on the main thread
     *
     * @param path            a path to wait on
     * @param afterProcessing a consumer to be called
     */
    public static void awaitProcessing(@Nullable Path path, Consumer<@Nullable Path> afterProcessing) {
        if (path != null && !path.isProcessed() && path instanceof AsyncPath asyncPath) {
            asyncPath.postProcessing(() ->
                MinecraftServer.getServer().scheduleOnMain(() -> afterProcessing.accept(path))
            );
        } else {
            afterProcessing.accept(path);
        }
    }

    private static BlockingQueue<Runnable> getQueueImpl() {
        final int queueCapacity = AsyncPathfinding.asyncPathfindingQueueSize;

        return new LinkedBlockingQueue<>(queueCapacity);
    }
}
