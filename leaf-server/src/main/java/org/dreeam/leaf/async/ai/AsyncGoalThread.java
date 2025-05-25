package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {

    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async Goal Thread");
        this.setDaemon(false);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(@NotNull MinecraftServer server) {
        while (server.isRunning()) {
            boolean retry = false;
            for (ServerLevel level : server.getAllLevels()) {
                retry |= level.asyncGoalExecutor.runAll();
                Thread.yield();
            }

            if (!retry) {
                LockSupport.parkNanos(10_000L);
            }
        }
    }
}
