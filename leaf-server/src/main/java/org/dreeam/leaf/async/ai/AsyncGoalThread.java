package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {
    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async Goal Thread");
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(MinecraftServer server) {
        while (server.isRunning()) {
            LockSupport.park();
            for (ServerLevel level : server.getAllLevels()) {
                var exec = level.asyncGoalExecutor;
                while (true) {
                    int id = exec.queue.recv();
                    if (id == Integer.MAX_VALUE) {
                        break;
                    }
                    if (exec.wake(id)) {
                        while (!exec.wake.send(id)) {
                            Thread.onSpinWait();
                        }
                    }
                }
            }
        }
    }
}
