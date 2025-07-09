package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;

import java.util.concurrent.*;

public class AsyncTracker {
    private static final String THREAD_PREFIX = "Leaf Async Tracker";
    public static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);
    public static ThreadPoolExecutor TRACKER_EXECUTOR = null;

    private AsyncTracker() {
    }

    public static void init(int threads) {
        if (TRACKER_EXECUTOR != null) {
            // Temp no-op
            return;
        }
        TRACKER_EXECUTOR = new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            Thread.ofPlatform()
                .uncaughtExceptionHandler(Util::onThreadException)
                .daemon(false)
                .priority(Thread.NORM_PRIORITY)
                .name(THREAD_PREFIX + " Thread")
                .factory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static void tick(ServerLevel world) {
        world.deferredTrackerTask = false;
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        Future<TrackerCtx> prev = world.trackerTask;
        if (MultithreadedTracker.noBlocking && prev != null && !prev.isDone()) {
            world.deferredTrackerTask = true;
            return;
        }
        world.trackerTask = TRACKER_EXECUTOR.submit(new TrackerTask(world, trackerEntities));
        if (prev != null) {
            try {
                prev.get().handle();
            } catch (InterruptedException ignore) {
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void onTickEnd(ServerLevel world) {
        Future<TrackerCtx> prev = world.trackerTask;
        if (prev == null) {
            return;
        }
        if (!prev.isDone()) {
            return;
        }
        try {
            prev.get(0L, TimeUnit.MILLISECONDS).handle();
            world.trackerTask = null;
        } catch (InterruptedException | TimeoutException ignore) {
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (!world.deferredTrackerTask) {
            return;
        }
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        world.trackerTask = TRACKER_EXECUTOR.submit(new TrackerTask(world, trackerEntities));
    }
}
