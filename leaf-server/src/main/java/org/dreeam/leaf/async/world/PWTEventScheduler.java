package org.dreeam.leaf.async.world;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PWTEventScheduler {
    private static volatile PWTEventScheduler instance;
    private final ExecutorService executor;
    private PWTEventScheduler() {
        this.executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("Leaf PWT Event Scheduler Thread - %d")
                .setDaemon(true)
                .setPriority(Thread.NORM_PRIORITY - 2)
                .build()
        );
    }

    public static PWTEventScheduler getScheduler() {
        if (instance == null) {
            synchronized (PWTEventScheduler.class) {
                if (instance == null) {
                    instance = new PWTEventScheduler();
                }
            }
        }
        return instance;
    }

    public void scheduleTask(Runnable task) {
        this.executor.execute(task);
    }
}
