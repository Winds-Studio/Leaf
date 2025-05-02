package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {
    private static final int SPIN_TRIES = 1000;

    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async Goal Thread");
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(MinecraftServer server) {
        int emptySpins = 0;

        while (server.isRunning()) {
            boolean didWork = false;
            for (ServerLevel level : server.getAllLevels()) {
                var exec = level.asyncGoalExecutor;
                boolean levelWork = false;
                while (true) {
                    int id = exec.queue.recv();
                    if (id == Integer.MAX_VALUE) {
                        break;
                    }
                    levelWork = true;
                    if (exec.wake(id)) {
                        while (!exec.wake.send(id)) {
                            Thread.onSpinWait();
                        }
                    }
                }
                didWork |= levelWork;
            }
            // Adaptive parking
            if (didWork) {
                emptySpins = 0; // Reset counter when work was done
            } else {
                emptySpins++;
                if (emptySpins > SPIN_TRIES) {
                    LockSupport.park(); // Only park after several empty spins
                    emptySpins = 0;
                } else {
                    Thread.onSpinWait(); // Yield to other threads but don't park
                }
            }
        }
    }
}
