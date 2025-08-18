package org.dreeam.leaf.async;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.async.path.AsyncPathProcessor;
import org.dreeam.leaf.async.tracker.AsyncTracker;

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

        if (AsyncTracker.TRACKER_EXECUTOR != null) {
            LOGGER.info("Waiting for entity tracker executor to shutdown...");
            AsyncTracker.TRACKER_EXECUTOR.shutdown();
            try {
                AsyncTracker.TRACKER_EXECUTOR.join(10_000L);
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
    }
}
