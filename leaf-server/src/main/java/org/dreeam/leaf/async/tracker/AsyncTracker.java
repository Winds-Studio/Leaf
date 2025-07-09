package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        Entity[] iter = new Entity[trackerEntitiesSize];
        System.arraycopy(trackerEntitiesRaw, 0, iter, 0, trackerEntitiesSize);
        Future<TrackerCtx> prev = world.trackerTask;
        world.trackerTask = TRACKER_EXECUTOR.submit(new TrackerTask(world, iter));
        if (prev != null) {
            try {
                prev.get().handle();
            } catch (InterruptedException ignore) {
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void tryHandle(ServerLevel world) {
        Future<TrackerCtx> prev = world.trackerTask;
        if (prev != null) {
            try {
                prev.get(0L, TimeUnit.MILLISECONDS).handle();
                world.trackerTask = null;
            } catch (InterruptedException | TimeoutException ignore) {
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public final static class TrackerTask implements Callable<TrackerCtx> {
        public final ServerLevel world;
        private final Entity[] entities;

        public TrackerTask(ServerLevel world, Entity[] entities) {
            this.world = world;
            this.entities = entities;
        }

        @Override
        public TrackerCtx call() throws Exception {
            NearbyPlayers nearbyPlayers = world.moonrise$getNearbyPlayers();
            TrackerCtx ctx = new TrackerCtx(this.world);
            for (final Entity entity : entities) {
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                NearbyPlayers.TrackedChunk trackedChunk = nearbyPlayers.getChunk(entity.chunkPosition());

                tracker.leafTick(ctx, trackedChunk);
                boolean flag = false;
                if (tracker.moonrise$hasPlayers()) {
                    flag = true;
                } else {
                    // may read old value
                    FullChunkStatus status = ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkStatus();
                    // removed in world
                    if (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                        flag = true;
                    }
                }
                if (flag) {
                    tracker.serverEntity.leafSendChanges(ctx, tracker);
                }
            }
            return ctx;
        }
    }
}
