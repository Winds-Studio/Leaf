package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async AI Thread - " + COUNTER.getAndAdd(1));
        this.setDaemon(false);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(@NotNull MinecraftServer server) {
        while (server.isRunning()) {
            boolean success = false;
            for (ServerLevel level : server.getAllLevels()) {
                success |= level.asyncGoalExecutor.runAll();
            }
            Thread.yield();
            if (!success) {
                LockSupport.parkNanos(10_000L);
            }
        }
    }
}
