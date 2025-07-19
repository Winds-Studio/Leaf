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

    private static final int QUEUE_MASK = QUEUE - 1; // For fast modulo (assuming QUEUE is power of 2)
    private static final int MIN_CHUNK_SHIFT = 4; // log2(16) = 4
    private static final int THREADS_MASK = Integer.highestOneBit(THREADS) - 1; // For power of 2 operations

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

        // Instead of: entities.length <= THREADS * MIN_CHUNK
        EntitySlice[] slices = entities.length <= (THREADS << MIN_CHUNK_SHIFT)
            ? slice.chunks(MIN_CHUNK)
            : slice.splitEvenly(THREADS);

        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slices.length];

        for (int i = 0; i < futures.length; i++) {
            futures[i] = TRACKER_EXECUTOR.submitOrRun(new TrackerTask(world, slices[i]));
        }

        TRACKER_EXECUTOR.unpack();
        world.trackerTask = futures;
    }

    public static void onEntitiesTickEnd(ServerLevel world) {
        Future<TrackerCtx>[] task = world.trackerTask;

        if (task == null) {
            return;
        }

        int completedMask = 0;
        int totalMask = (1 << task.length) - 1;

        for (int i = 0; i < task.length; i++) {
            if (task[i].isDone()) {
                completedMask |= (1 << i);
            }
        }

        if (completedMask == totalMask) {
            handle(world, task);
        }
    }

    public static void onTickEnd(MinecraftServer server) {
        ServerLevel[] worlds = server.getAllLevels().toArray(ServerLevel[]::new);

        for (int i = 0; i < worlds.length; i++) {
            ServerLevel world = worlds[i];
            Future<TrackerCtx>[] task = world.trackerTask;

            if (task != null) {
                handle(world, task);
            }
        }
    }

    private static void handle(ServerLevel world, Future<TrackerCtx>[] futures) {
        try {
            TrackerCtx ctx = futures[0].get();

            int i = 1;
            int len = futures.length;

            for (; i < (len & ~3); i += 4) {
                ctx.join(futures[i].get());
                ctx.join(futures[i + 1].get());
                ctx.join(futures[i + 2].get());
                ctx.join(futures[i + 3].get());
            }

            for (; i < len; i++) {
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
