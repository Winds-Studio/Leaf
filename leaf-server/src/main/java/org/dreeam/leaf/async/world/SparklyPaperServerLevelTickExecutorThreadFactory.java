package org.dreeam.leaf.async.world;

import ca.spottedleaf.moonrise.common.util.TickThread;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;

public class SparklyPaperServerLevelTickExecutorThreadFactory implements ThreadFactory {

    private final String worldName;

    public SparklyPaperServerLevelTickExecutorThreadFactory(final String worldName) {
        this.worldName = worldName;
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        TickThread.ServerLevelTickThread tickThread = new TickThread.ServerLevelTickThread(runnable, "Leaf World Ticking Thread - " + this.worldName);

        if (tickThread.isDaemon()) {
            tickThread.setDaemon(false);
        }

        if (tickThread.getPriority() != 5) {
            tickThread.setPriority(5);
        }

        return tickThread;
    }
}
