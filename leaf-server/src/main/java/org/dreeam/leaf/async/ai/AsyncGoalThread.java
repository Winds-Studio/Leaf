package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.OptionalInt;
import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {

    public static volatile boolean RUNNING = true;
    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async Goal Thread");
        this.setDaemon(false);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(MinecraftServer server) {
        while (RUNNING) {
            boolean retry = false;
            for (ServerLevel level : server.getAllLevels()) {
                retry |= level.asyncGoalExecutor.wakeAll();
            }

            if (!retry) {
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }
}
