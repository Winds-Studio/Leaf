package org.dreeam.leaf.async.chunk;

import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncChunkSend {
    public static final ExecutorService POOL = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setPriority(Thread.NORM_PRIORITY - 2)
            .setNameFormat("Leaf Async Chunk Send Thread")
            .setUncaughtExceptionHandler(Util::onThreadException)
            .setThreadFactory(AsyncChunkSendThread::new)
            .build(),
        new ThreadPoolExecutor.DiscardPolicy()
    );
    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Chunk Send");
}
