package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
// CHANGE: No longer need LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultithreadedTracker {

    private static final String THREAD_PREFIX = "Leaf Async Tracker";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);
    private static long lastWarnMillis = System.currentTimeMillis();
    public static ThreadPoolExecutor TRACKER_EXECUTOR = null;

    private MultithreadedTracker() {
    }

    public static void init() {
        if (TRACKER_EXECUTOR == null) {
            TRACKER_EXECUTOR = new ThreadPoolExecutor(
                getCorePoolSize(),
                getMaxPoolSize(),
                getKeepAliveTime(), TimeUnit.SECONDS,
                getQueueImpl(),
                getThreadFactory(),
                getRejectedPolicy()
            );
        } else {
            // Temp no-op
            //throw new IllegalStateException();
        }
    }

    public static void tick(ServerLevel level) {
        try {
            if (!org.dreeam.leaf.config.modules.async.MultithreadedTracker.compatModeEnabled) {
                tickAsync(level);
            } else {
                tickAsyncWithCompatMode(level);
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred while executing async task.", e);
        }
    }

    private static void tickAsync(ServerLevel level) {
        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();

        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        final int trackerEntitiesSize = trackerEntities.size();
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();

        // Move tracking to off-main
        TRACKER_EXECUTOR.execute(() -> {
            for (int i = 0; i < trackerEntitiesSize; i++) {
                Entity entity = trackerEntitiesRaw[i];
                if (entity == null) continue;

                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();

                if (tracker == null) continue;

                synchronized (tracker) {
                    NearbyPlayers.TrackedChunk trackedChunk = nearbyPlayers.getChunk(entity.chunkPosition());
                    tracker.moonrise$tick(trackedChunk);
                    tracker.serverEntity.sendChanges();
                }
            }
        });
    }

    private static void tickAsyncWithCompatMode(ServerLevel level) {
        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();

        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        final int trackerEntitiesSize = trackerEntities.size();
        final List<TrackingData> trackingTasks = new ArrayList<>(trackerEntitiesSize);

        for (int i = 0; i < trackerEntitiesSize; i++) {
            final Entity entity = trackerEntitiesRaw[i];
            if (entity == null) continue;

            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();

            if (tracker == null) continue;

            synchronized (tracker) {
                trackingTasks.add(new TrackingData(tracker, nearbyPlayers.getChunk(entity.chunkPosition())));
            }
        }

        // batch submit tasks
        TRACKER_EXECUTOR.execute(() -> {
            for (final TrackingData task : trackingTasks) {
                final Runnable tick = task.tracker.leafTickCompact(task.trackedChunk);
                if (tick != null) {
                    tick.run();
                }
            }
            for (final TrackingData task : trackingTasks) {
                task.tracker.serverEntity.sendChanges();
            }
        });
    }

    // Original ChunkMap#newTrackerTick of Paper
    // Just for diff usage for future update
    private static void tickOriginal(ServerLevel level) {
        final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup entityLookup = (ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup) ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel) level).moonrise$getEntityLookup();

        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
            final Entity entity = trackerEntitiesRaw[i];
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkData().nearbyPlayers);
            if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker).moonrise$hasPlayers()
                || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                tracker.serverEntity.sendChanges();
            }
        }
    }

    private static int getCorePoolSize() {
        return 1;
    }

    private static int getMaxPoolSize() {
        return org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerMaxThreads;
    }

    private static long getKeepAliveTime() {
        return org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerKeepalive;
    }

    private static BlockingQueue<Runnable> getQueueImpl() {
        // A SynchronousQueue has no capacity and is used for direct hand-offs.
        // This makes the CallerRunsPolicy trigger immediately when all threads are busy.
        return new SynchronousQueue<>();
    }

    private static @NotNull ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
            .setThreadFactory(MultithreadedTrackerThread::new)
            .setNameFormat(THREAD_PREFIX + " Thread - %d")
            .setPriority(Thread.NORM_PRIORITY - 2)
            .setUncaughtExceptionHandler(Util::onThreadException)
            .build();
    }

    private static @NotNull RejectedExecutionHandler getRejectedPolicy() {
        final RejectedExecutionHandler callerRunsPolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        return (rejectedTask, executor) -> {
            final long currentTime = System.currentTimeMillis();
            if (currentTime - lastWarnMillis > 30000L) {
                LOGGER.warn("Async entity tracker is busy! Tracking tasks will be done in the server thread. Increasing max-threads in Leaf config may help.");
                lastWarnMillis = currentTime;
            }
            callerRunsPolicy.rejectedExecution(rejectedTask, executor);
        };
    }

    public static class MultithreadedTrackerThread extends Thread {

        public MultithreadedTrackerThread(Runnable runnable) {
            super(runnable);
        }
    }

    private static class TrackingData {
        final ChunkMap.TrackedEntity tracker;
        final NearbyPlayers.TrackedChunk trackedChunk;

        TrackingData(final ChunkMap.TrackedEntity tracker, final NearbyPlayers.TrackedChunk trackedChunk) {
            this.tracker = tracker;
            this.trackedChunk = trackedChunk;
        }
    }
}
