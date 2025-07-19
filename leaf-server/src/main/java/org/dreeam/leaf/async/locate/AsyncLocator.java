package org.dreeam.leaf.async.locate;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// Original project: https://github.com/thebrightspark/AsyncLocator
public class AsyncLocator {

    private static final ExecutorService LOCATING_EXECUTOR_SERVICE;

    private AsyncLocator() {
    }

    public static class AsyncLocatorThread extends TickThread {
        // Counter to track the number of threads created.
        private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

        public AsyncLocatorThread(Runnable run, String name) {
            super(run, name, THREAD_COUNTER.incrementAndGet());
        }

        @Override
        public void run() {
            super.run();
        }
    }

    static {
        // Retrieve thread count and keep-alive time from configuration.
        int threads = org.dreeam.leaf.config.modules.async.AsyncLocator.asyncLocatorThreads;
        long keepAlive = org.dreeam.leaf.config.modules.async.AsyncLocator.asyncLocatorKeepalive;

        // Using a fixed-size thread pool to reduce overhead in creating and tearing down threads under load.
        LOCATING_EXECUTOR_SERVICE = new ThreadPoolExecutor(
            threads,                // corePoolSize
            threads,                // maximumPoolSize
            keepAlive,              // keep-alive time
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder()
                .setThreadFactory(r -> new AsyncLocatorThread(r, "Leaf Async Locator Thread"))
                .setNameFormat("Leaf Async Locator Thread - %d")
                .setPriority(Thread.NORM_PRIORITY - 2)
                .build()
        );
    }

    public static void shutdownExecutorService() {
        if (LOCATING_EXECUTOR_SERVICE != null) {
            LOCATING_EXECUTOR_SERVICE.shutdown();
        }
    }

    /**
     * Searches for a feature using ServerLevel.findNearestMapStructure(TagKey, BlockPos, int, boolean)
     * and returns a LocateTask that encapsulates the futures.
     */
    public static LocateTask<BlockPos> locate(
        ServerLevel level,
        TagKey<Structure> structureTag,
        BlockPos pos,
        int searchRadius,
        boolean skipKnownStructures
    ) {
        CompletableFuture<BlockPos> future = CompletableFuture.supplyAsync(() ->
                level.findNearestMapStructure(structureTag, pos, searchRadius, skipKnownStructures),
            LOCATING_EXECUTOR_SERVICE
        );
        return new LocateTask<>(level.getServer(), future, future);
    }

    /**
     * Searches for a feature using ChunkGenerator.findNearestMapStructure(ServerLevel, HolderSet, BlockPos, int, boolean)
     * and returns a LocateTask that encapsulates the futures.
     */
    public static LocateTask<Pair<BlockPos, Holder<Structure>>> locate(
        ServerLevel level,
        HolderSet<Structure> structureSet,
        BlockPos pos,
        int searchRadius,
        boolean skipKnownStructures
    ) {
        CompletableFuture<Pair<BlockPos, Holder<Structure>>> future = CompletableFuture.supplyAsync(() ->
                level.getChunkSource().getGenerator().findNearestMapStructure(level, structureSet, pos, searchRadius, skipKnownStructures),
            LOCATING_EXECUTOR_SERVICE
        );
        return new LocateTask<>(level.getServer(), future, future);
    }

    /**
     * Holds the futures for an async locate task and provides helper functions.
     * The completableFuture completes when the locate task is finished, containing the result.
     * The taskFuture represents the underlying Runnable task in the executor service.
     */
    public record LocateTask<T>(
        MinecraftServer server,
        CompletableFuture<T> completableFuture,
        java.util.concurrent.Future<?> taskFuture
    ) {
        /**
         * Adds an action that runs when the locate operation completes,
         * executing on the locate thread.
         */
        public LocateTask<T> then(Consumer<T> action) {
            completableFuture.thenAccept(action);
            return this;
        }

        /**
         * Adds an action that runs when the locate operation completes,
         * scheduling execution on the main server thread.
         */
        public LocateTask<T> thenOnServerThread(Consumer<T> action) {
            completableFuture.thenAccept(pos -> server.scheduleOnMain(() -> action.accept(pos)));
            return this;
        }

        /**
         * Cancels both the completableFuture and taskFuture.
         */
        public void cancel() {
            taskFuture.cancel(true);
            completableFuture.cancel(false);
        }
    }
}
