package org.dreeam.leaf.async.ai;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AsyncGoalExecutor {
    @Nullable
    public static java.util.concurrent.ExecutorService EXECUTOR;

    public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("Leaf Async Entity Lookup");

    public static void runTasks(List<Runnable> tasks) {
        for (Runnable task : tasks) {
            task.run();
        }
    }
}
