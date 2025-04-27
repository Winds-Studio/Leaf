package org.dreeam.leaf.async.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class AsyncGoalExecutor {
    public static ExecutorService EXECUTOR;

    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Entity Lookup");

    public static void runTasks(List<Runnable> tasks) {
        for (Runnable task : tasks) {
            task.run();
        }
    }
}
