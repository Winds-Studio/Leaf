package org.dreeam.leaf.async;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.async.ai.AsyncGoalThread;
import org.dreeam.leaf.async.path.AsyncPathProcessor;
import org.dreeam.leaf.async.tracker.MultithreadedTracker;

import java.util.concurrent.TimeUnit;

public class ShutdownExecutors {

    public static final Logger LOGGER = LogManager.getLogger("Leaf");

    public static void shutdown(MinecraftServer server) {
        if (server.mobSpawnExecutor != null && server.mobSpawnExecutor.thread.isAlive()) {
            LOGGER.info("Waiting for mob spawning thread to shutdown...");
            try {
                server.mobSpawnExecutor.join(3000L);
            } catch (InterruptedException ignored) {
            }
        }

        if (AsyncPlayerDataSaving.IO_POOL != null) {
            LOGGER.info("Waiting for player I/O executor to shutdown...");
            AsyncPlayerDataSaving.IO_POOL.shutdown();
            try {
                AsyncPlayerDataSaving.IO_POOL.awaitTermination(60L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        if (server.asyncGoalThread != null) {
            LOGGER.info("Waiting for mob target finding thread to shutdown...");
            AsyncGoalThread.RUNNING = false;
            try {
                server.asyncGoalThread.join(3000L);
            } catch (InterruptedException ignored) {
            }
        }

        if (MultithreadedTracker.TRACKER_EXECUTOR != null) {
            LOGGER.info("Waiting for mob tracker executor to shutdown...");
            MultithreadedTracker.TRACKER_EXECUTOR.shutdown();
            try {
                MultithreadedTracker.TRACKER_EXECUTOR.awaitTermination(10L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        if (AsyncPathProcessor.PATH_PROCESSING_EXECUTOR != null) {
            LOGGER.info("Waiting for mob pathfinding executor to shutdown...");
            AsyncPathProcessor.PATH_PROCESSING_EXECUTOR.shutdown();
            try {
                AsyncPathProcessor.PATH_PROCESSING_EXECUTOR.awaitTermination(10L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        if (org.dreeam.leaf.async.container.AsyncContainerBroadcaster.CONTAINER_POOL != null) {
            LOGGER.info("Waiting for container broadcast executor to shutdown...");
            org.dreeam.leaf.async.container.AsyncContainerBroadcaster.CONTAINER_POOL.shutdown();
            try {
                org.dreeam.leaf.async.container.AsyncContainerBroadcaster.CONTAINER_POOL.awaitTermination(10L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
