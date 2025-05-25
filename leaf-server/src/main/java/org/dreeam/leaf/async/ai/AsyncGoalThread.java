package org.dreeam.leaf.async.ai;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.LockSupport;

public class AsyncGoalThread extends Thread {

    public final TickTimes tickTimes = new TickTimes(3000);

    public AsyncGoalThread(final MinecraftServer server) {
        super(() -> run(server), "Leaf Async AI Thread");
        this.setDaemon(false);
        this.setUncaughtExceptionHandler(Util::onThreadException);
        this.setPriority(Thread.NORM_PRIORITY - 1);
        this.start();
    }

    private static void run(@NotNull MinecraftServer server) {
        int tickCount = 0;
        while (server.isRunning()) {
            boolean success = false;
            long nanos = Util.getNanos();
            for (ServerLevel level : server.getAllLevels()) {
                success |= level.asyncGoalExecutor.runAll();
            }

            if (success) {
                long e = Util.getNanos() - nanos;
                server.asyncGoalThread.tickTimes.add(tickCount, e);
                tickCount++;
            }

            Thread.yield();
            if (!success) {
                LockSupport.parkNanos(10_000L);
            }
        }
    }

    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
}
