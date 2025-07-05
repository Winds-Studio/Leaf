package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.galemc.gale.virtualthread.VirtualThreadService;

import java.util.ArrayList;
import java.util.List;

public class MultithreadedTracker {

    private static final String THREAD_PREFIX = "Leaf Async Tracker";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);

    private MultithreadedTracker() {
    }

    public static void tick(ServerLevel level) {
        try {
            final Runnable task = org.dreeam.leaf.config.modules.async.MultithreadedTracker.compatModeEnabled
                ? createCompatTask(level)
                : createAsyncTask(level);

            if (task == null) {
                return;
            }

            VirtualThreadService.get().start(task);

        } catch (Exception e) {
            LOGGER.error("Error occurred while submitting async tracker task.", e);
        }
    }

    @Nullable
    private static Runnable createAsyncTask(ServerLevel level) {
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();
        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        if (trackerEntities.size() == 0) return null;

        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final int trackerEntitiesSize = trackerEntities.size();
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();

        return () -> {
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
        };
    }

    @Nullable
    private static Runnable createCompatTask(ServerLevel level) {
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();
        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        if (trackerEntities.size() == 0) return null;

        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final int trackerEntitiesSize = trackerEntities.size();
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
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

        return () -> {
            for (final TrackingData task : trackingTasks) {
                final Runnable tick = task.tracker.leafTickCompact(task.trackedChunk);
                if (tick != null) {
                    tick.run();
                }
            }
            for (final TrackingData task : trackingTasks) {
                task.tracker.serverEntity.sendChanges();
            }
        };
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
