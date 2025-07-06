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

import java.util.concurrent.*;

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
                org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerThreads,
                org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerThreads,
                0L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerQueueSize),
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
        if (trackerEntitiesSize == 0) return;
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
        if (trackerEntitiesSize == 0) return;
        final Runnable[] sendChangesTasks = new Runnable[trackerEntitiesSize];
        final Runnable[] tickTask = new Runnable[trackerEntitiesSize];
        int index = 0;

        for (int i = 0; i < trackerEntitiesSize; i++) {
            Entity entity = trackerEntitiesRaw[i];
            if (entity == null) continue;

            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();

            if (tracker == null) continue;

            synchronized (tracker) {
                tickTask[index] = tracker.leafTickCompact(nearbyPlayers.getChunk(entity.chunkPosition()));
                sendChangesTasks[index] = tracker.serverEntity::sendChanges; // Collect send changes to task array
            }
            index++;
        }

        // batch submit tasks
        TRACKER_EXECUTOR.execute(() -> {
            for (final Runnable tick : tickTask) {
                if (tick == null) continue;

                tick.run();
            }
            for (final Runnable sendChanges : sendChangesTasks) {
                if (sendChanges == null) continue;

                sendChanges.run();
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

    private static @NotNull ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
            .setThreadFactory(MultithreadedTrackerThread::new)
            .setNameFormat(THREAD_PREFIX + " Thread - %d")
            .setPriority(Thread.NORM_PRIORITY)
            .setUncaughtExceptionHandler(Util::onThreadException)
            .setDaemon(false)
            .build();
    }

    private static @NotNull RejectedExecutionHandler getRejectedPolicy() {
        return (r, executor) -> {
            if (!executor.isShutdown()) {
                executor.getQueue().poll();
                executor.execute(r);
                if (System.currentTimeMillis() - lastWarnMillis > 30000L) {
                    LOGGER.warn("Async entity tracker is busy! Oldest tasks will be discard. Increasing threads in Leaf config may help.");
                    lastWarnMillis = System.currentTimeMillis();
                }
            }
        };
    }

    public static class MultithreadedTrackerThread extends Thread {

        public MultithreadedTrackerThread(Runnable runnable) {
            super(runnable);
        }
    }
}
