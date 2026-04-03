package org.dreeam.leaf.async.chunk;

import net.minecraft.util.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AsyncChunkSend {

    public static final ExecutorService POOL = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        Thread.ofPlatform()
            .priority(Thread.NORM_PRIORITY - 1)
            .uncaughtExceptionHandler(Util::onThreadException)
            .name("Leaf Async Chunk Sender Thread")
            .factory(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
