package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.OptionalInt;
import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {

    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async Goal Thread");
        this.setDaemon(false);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(MinecraftServer server) {
        while (server.isRunning()) {
            boolean retry = false;
            for (ServerLevel level : server.getAllLevels()) {
                var exec = level.asyncGoalExecutor;
                while (true) {
                    OptionalInt result = exec.queue.recv();
                    if (result.isEmpty()) {
                        break;
                    }
                    int id = result.getAsInt();
                    retry = true;
                    if (exec.wake(id)) {
                        while (!exec.wake.send(id)) {
                            Thread.onSpinWait();
                        }
                    }
                }

                Thread.yield();
            }

            if (!retry) {
                LockSupport.parkNanos(10_000L);
            }
        }
    }
}
