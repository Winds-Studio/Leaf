package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;

import java.util.concurrent.*;

public final class AsyncTracker {
    private static final String THREAD_NAME = "Leaf Async Tracker Thread";
    public static TrackerExecutor TRACKER_EXECUTOR = null;

    private AsyncTracker() {
    }

    public static void init(int threads, int queue) {
        if (TRACKER_EXECUTOR != null) {
            throw new IllegalStateException();
        }
        TRACKER_EXECUTOR = new TrackerExecutor(
            threads,
            queue
        );
    }

    public static void tick(ServerLevel world) {
        world.deferredTrackerTask = false;
        Future<TrackerCtx>[] prev = world.trackerTask;
        if (MultithreadedTracker.nonblocking && prev != null) {
            for (Future<TrackerCtx> fut : prev) {
                if (!fut.isDone()) {
                    return;
                }
            }
            try {
                TrackerCtx ctx = new TrackerCtx(world);
                for (Future<TrackerCtx> fut : prev) {
                    ctx.join(fut.get());
                }
                world.trackerTask = null;
                ctx.handle();
            } catch (InterruptedException ignore) {
                return;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        Entity[] entities = new Entity[trackerEntitiesSize];
        System.arraycopy(trackerEntitiesRaw, 0, entities, 0, trackerEntitiesSize);
        EntitySlice[] slice = new EntitySlice(entities).splitEvenly(MultithreadedTracker.parts);
        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slice.length];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = TRACKER_EXECUTOR.submitOrRun(new TrackerTask(world, slice[i]));
        }
        TRACKER_EXECUTOR.unpack();
        world.trackerTask = futures;
    }

    public static void onEntitiesTickEnd(ServerLevel world) {
        Future<TrackerCtx>[] prev = world.trackerTask;
        world.deferredTrackerTask = true;
        if (prev == null) {
            return;
        }
        if (MultithreadedTracker.nonblocking) {
            for (Future<TrackerCtx> fut : prev) {
                if (!fut.isDone()) {
                    return;
                }
            }
            return;
        }
        world.deferredTrackerTask = false;
        try {
            TrackerCtx ctx = new TrackerCtx(world);
            for (Future<TrackerCtx> fut : prev) {
                ctx.join(fut.get());
            }
            world.trackerTask = null;
            ctx.handle();
        } catch (InterruptedException ignore) {
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
