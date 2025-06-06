package org.dreeam.leaf.async;

import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.async.tracker.MultithreadedTracker;

public class ShutdownExecutors {
    public static void shutdown(MinecraftServer server) {

        if (server.mobSpawnExecutor != null) {
            try {
                server.mobSpawnExecutor.kill();
            } catch (InterruptedException ignored) {
            }
        }

        if (AsyncPlayerDataSaving.IO_POOL != null) {
            AsyncPlayerDataSaving.IO_POOL.shutdown();
            try {
                AsyncPlayerDataSaving.IO_POOL.awaitTermination(300L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        if (server.asyncGoalThread != null) {
            try {
                server.asyncGoalThread.join();
            } catch (InterruptedException ignored) {
            }
        }

        if (MultithreadedTracker.TRACKER_EXECUTOR != null) {
            MultithreadedTracker.TRACKER_EXECUTOR.shutdown();
            try {
                MultithreadedTracker.TRACKER_EXECUTOR.awaitTermination(10L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
