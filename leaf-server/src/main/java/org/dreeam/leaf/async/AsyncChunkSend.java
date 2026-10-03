package org.dreeam.leaf.async;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.dreeam.leaf.config.modules.async.AsyncChunkSending;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncChunkSend {
    public static final ExecutorService POOL = newPool();

    private static ExecutorService newPool() {
        int threads = Math.max(1, AsyncChunkSending.maxThreads);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            threads, threads,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("Async Chunk Sending - %d").setDaemon(true).setPriority(Thread.NORM_PRIORITY).build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
