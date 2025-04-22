package org.dreeam.leaf.async.ai;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncGoalExecutor {
    public static final java.util.concurrent.ExecutorService EXECUTOR = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(128),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat("Leaf Async Target Finding Thread")
            .setDaemon(true)
            .setPriority(Thread.NORM_PRIORITY - 2)
            .build(), new ThreadPoolExecutor.CallerRunsPolicy());
}
