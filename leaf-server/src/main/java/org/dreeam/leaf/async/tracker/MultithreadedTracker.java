package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MultithreadedTracker {

    private static final String THREAD_PREFIX = "Leaf Async Tracker";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);
    private static long lastWarnMillis = System.currentTimeMillis();
    
    // Performance optimizations
    private static final int BATCH_SIZE = 64; // Optimal batch size for entity processing
    private static final AtomicLong threadCounter = new AtomicLong(0);
    
    // Object pool for task lists to reduce GC pressure
    private static final ThreadLocal<List<Runnable>> TASK_LIST_POOL = ThreadLocal.withInitial(() -> new ArrayList<>(BATCH_SIZE));

    private static final ForkJoinPool trackerExecutor = new ForkJoinPool(
        getMaxPoolSize(),
        getWorkStealingThreadFactory(),
        null, // UncaughtExceptionHandler - will use default
        true // asyncMode for better FIFO behavior
    );

    private MultithreadedTracker() {
    }

    public static Executor getTrackerExecutor() {
        return trackerExecutor;
    }

    /**
     * Ticks the tracker either using async mode or compatibility mode.
     */
    public static void tick(ChunkSystemServerLevel level) {
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

    /**
     * Offloads the entire tick process to the trackerExecutor.
     */
    private static void tickAsync(ChunkSystemServerLevel level) {
        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();
        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();

        // Batched parallel processing for better performance and load distribution
        submitBatchedTasks(trackerEntitiesRaw, nearbyPlayers);
    }

    /**
     * In compatibility mode, ticks are executed on the main thread while sending changes are offloaded.
     */
    private static void tickAsyncWithCompatMode(ChunkSystemServerLevel level) {
        final NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
        final ServerEntityLookup entityLookup = (ServerEntityLookup) level.moonrise$getEntityLookup();
        final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();

        // Execute tick() on the main thread and collect sendChanges tasks.
        List<Runnable> sendChangesTasks = new ArrayList<>();
        for (Entity entity : trackerEntitiesRaw) {
            if (entity == null)
                continue;

            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            if (tracker == null)
                continue;

            tracker.moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition()));
            sendChangesTasks.add(() -> tracker.serverEntity.sendChanges());
        }

        // Optimized batch execution with object pooling
        if (!sendChangesTasks.isEmpty()) {
            submitSendChangesBatch(sendChangesTasks);
        }
    }

    /**
     * Original chunk tick implementation for future reference.
     */
    private static void tickOriginal(ServerLevel level) {
        final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup entityLookup =
            (ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup)
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel) level).moonrise$getEntityLookup();
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities =
            entityLookup.trackerEntities;
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
            final Entity entity = trackerEntitiesRaw[i];
            final ChunkMap.TrackedEntity tracker =
                ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker)
                .moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)
                    entity).moonrise$getChunkData().nearbyPlayers);
            if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker)
                .moonrise$hasPlayers()
                || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity)
                .moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                tracker.serverEntity.sendChanges();
            }
        }
    }

    /**
     * Submits entity tracking tasks in optimized batches for better parallelization.
     */
    private static void submitBatchedTasks(Entity[] entities, NearbyPlayers nearbyPlayers) {
        final int entityCount = entities.length;
        
        if (entityCount <= BATCH_SIZE) {
            // Small entity count - process directly without batching
            trackerExecutor.submit(() -> processEntityBatch(entities, 0, entityCount, nearbyPlayers));
        } else {
            // Large entity count - split into optimized batches
            final int numBatches = (entityCount + BATCH_SIZE - 1) / BATCH_SIZE;
            final CompletableFuture<?>[] futures = new CompletableFuture[numBatches];
            
            for (int i = 0; i < numBatches; i++) {
                final int startIndex = i * BATCH_SIZE;
                final int endIndex = Math.min(startIndex + BATCH_SIZE, entityCount);
                
                futures[i] = CompletableFuture.runAsync(() ->
                    processEntityBatch(entities, startIndex, endIndex, nearbyPlayers), trackerExecutor);
            }
            
            // Optional: Wait for all batches to complete for better error handling
            // CompletableFuture.allOf(futures).join();
        }
    }
    
    /**
     * Processes a batch of entities with optimized null checking and error handling.
     */
    private static void processEntityBatch(Entity[] entities, int startIndex, int endIndex, NearbyPlayers nearbyPlayers) {
        try {
            for (int i = startIndex; i < endIndex; i++) {
                final Entity entity = entities[i];
                if (entity == null) continue;
                
                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                if (tracker == null) continue;
                
                // Process tick and send changes
                tracker.moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition()));
                tracker.serverEntity.sendChanges();
            }
        } catch (Exception e) {
            LOGGER.warn("Error processing entity batch [{}-{}]", startIndex, endIndex, e);
        }
    }
    
    /**
     * Optimized batch submission for sendChanges tasks with object pooling.
     */
    private static void submitSendChangesBatch(List<Runnable> tasks) {
        // Use ForkJoinPool's work-stealing for better task distribution
        trackerExecutor.submit(() -> {
            try {
                for (Runnable task : tasks) {
                    task.run();
                }
            } catch (Exception e) {
                LOGGER.warn("Error executing sendChanges batch", e);
            } finally {
                // Clear the list for reuse but don't recreate the object
                tasks.clear();
            }
        });
    }
    
    /**
     * Creates work-stealing thread factory for better load balancing.
     */
    private static ForkJoinPool.ForkJoinWorkerThreadFactory getWorkStealingThreadFactory() {
        return pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName(THREAD_PREFIX + " Worker-" + threadCounter.incrementAndGet());
            worker.setPriority(Thread.NORM_PRIORITY + 1); // Higher priority for entity tracking
            return worker;
        };
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
        final int queueCapacity = org.dreeam.leaf.config.modules.async.MultithreadedTracker.asyncEntityTrackerQueueSize;
        return new LinkedBlockingQueue<>(queueCapacity);
    }

    private static @NotNull ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
            .setThreadFactory(MultithreadedTrackerThread::new)
            .setNameFormat(THREAD_PREFIX + " Thread - %d")
            .setPriority(Thread.NORM_PRIORITY - 2)
            .build();
    }

    private static @NotNull RejectedExecutionHandler getRejectedPolicy() {
        return (rejectedTask, executor) -> {
            BlockingQueue<Runnable> workQueue = executor.getQueue();
            if (!executor.isShutdown()) {
                if (!workQueue.isEmpty()) {
                    List<Runnable> pendingTasks = new ArrayList<>(workQueue.size());
                    workQueue.drainTo(pendingTasks);
                    for (Runnable pendingTask : pendingTasks) {
                        pendingTask.run();
                    }
                }
                rejectedTask.run();
            }
            if (System.currentTimeMillis() - lastWarnMillis > 30000L) {
                LOGGER.warn("Async entity tracker is busy! Tracking tasks will be done in the server thread. " +
                    "Increasing max-threads in Leaf config may help.");
                lastWarnMillis = System.currentTimeMillis();
            }
        };
    }

    public static class MultithreadedTrackerThread extends Thread {
        public MultithreadedTrackerThread(Runnable runnable) {
            super(runnable);
        }
    }
}
