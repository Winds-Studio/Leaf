package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.async.FixedThreadExecutor;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;
import org.dreeam.leaf.util.EntitySlice;

import java.util.concurrent.*;

public final class AsyncTracker {
    private static final String THREAD_NAME = "Leaf Async Tracker Thread";
    public static final boolean ENABLED = MultithreadedTracker.enabled;
    public static final int QUEUE = 1024;
    public static final int MIN_CHUNK = 16;
    public static final int THREADS = MultithreadedTracker.threads;
    public static final FixedThreadExecutor TRACKER_EXECUTOR = ENABLED ? new FixedThreadExecutor(
        THREADS,
        QUEUE,
        THREAD_NAME
    ) : null;

    private AsyncTracker() {
    }

    public static void init() {
        if (TRACKER_EXECUTOR == null || !ENABLED) {
            throw new IllegalStateException();
        }
    }

    public static void tick(ServerLevel world) {
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        Entity[] entities = new Entity[trackerEntitiesSize];
        System.arraycopy(trackerEntitiesRaw, 0, entities, 0, trackerEntitiesSize);
        EntitySlice slice = new EntitySlice(entities);
        boolean usePWT = org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking.enabled;
        FixedThreadExecutor executor = usePWT ? world.leafAsyncTrackerExecutor : TRACKER_EXECUTOR;
        if (executor == null) {
            // Executor might not be initialized, skip this tick
            return;
        }

        int threadCount = usePWT ? 1 : THREADS;
        EntitySlice[] slices = entities.length <= threadCount * MIN_CHUNK ? slice.chunks(MIN_CHUNK) : slice.splitEvenly(threadCount);

        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slices.length];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = executor.submitOrRun(new TrackerTask(world, slices[i]));
        }
        executor.unpack();
        world.trackerTask = futures;
    }

    public static void onEntitiesTickEnd(ServerLevel world) {
        if (!org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking.enabled) {
            return;
        }
        Future<TrackerCtx>[] task = world.trackerTask;
        if (task == null) {
            return;
        }
        for (Future<TrackerCtx> fut : task) {
            if (!fut.isDone()) {
                return;
            }
        }
        handle(world, task);
    }

    public static void onTickEnd(MinecraftServer server) {
        if (org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking.enabled) {
            return;
        }
        for (ServerLevel world : server.getAllLevels()) {
            Future<TrackerCtx>[] task = world.trackerTask;
            if (task != null) {
                handle(world, task);
            }
        }
    }

    private static void handle(ServerLevel world, Future<TrackerCtx>[] futures) {
        try {
            TrackerCtx ctx = futures[0].get();
            for (int i = 1; i < futures.length; i++) {
                ctx.join(futures[i].get());
            }
            world.trackerTask = null;
            ctx.handle();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
