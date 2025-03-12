package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

public class QueuedPacketSending extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-packet-sending";
    }

    // General settings
    public static boolean enabled = false;

    // Queue settings
    public static int batchSize = 512;
    public static int flushFrequency = 1;
    public static boolean prioritizeImportantPackets = true;

    // Thread pool settings
    public static int threadPoolSize = 4;
    public static int queueCapacity = 1024; // in case if it leaks, will most likely remove this later on
    private static ExecutorService PACKET_THREAD_EXECUTOR = null;

    public static ExecutorService getPacketThreadExecutor() {
        if (PACKET_THREAD_EXECUTOR == null && enabled) {
            PACKET_THREAD_EXECUTOR = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity), // Using bounded queue to prevent memory issues
                new ThreadFactoryBuilder()
                    .setNameFormat("Packet-Processor-%d")
                    .setDaemon(true)
                    .setUncaughtExceptionHandler((t, e) -> {
                        LeafConfig.LOGGER.error("Uncaught exception in Packet Processor Thread", e);
                    })
                    .build(),
                new ThreadPoolExecutor.CallerRunsPolicy() // If queue is full, run in caller thread as fallback
            );
        }
        return PACKET_THREAD_EXECUTOR;
    }

    public static void shutdown() {
        if (PACKET_THREAD_EXECUTOR != null) {
            PACKET_THREAD_EXECUTOR.shutdown();
            try {
                if (!PACKET_THREAD_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                    LeafConfig.LOGGER.warn("Packet processor thread pool did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            PACKET_THREAD_EXECUTOR = null;
        }
    }

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Optimizes player packet sending by using a queue-based approach with batched processing.
                This reduces main thread load and improves network efficiency compared to per-packet scheduling.
                """,
            """
                通过使用基于队列的批处理方法来优化玩家数据包发送。
                与每个数据包单独调度相比，这减少了主线程负载并提高了网络效率。
                """);

        // General settings
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);

        // Queue settings
        batchSize = config.getInt(getBasePath() + ".batch-size", batchSize,
            "Maximum number of packets to process in a single batch");
        flushFrequency = config.getInt(getBasePath() + ".flush-frequency", flushFrequency,
            "How often (in ticks) to flush the packet queue");
        prioritizeImportantPackets = config.getBoolean(getBasePath() + ".prioritize-important-packets",
            prioritizeImportantPackets, "Immediately process important packets like chat and keep-alive");

        // Thread pool settings
        threadPoolSize = config.getInt(getBasePath() + ".thread-pool-size", threadPoolSize);
        queueCapacity = config.getInt(getBasePath() + ".queue-capacity", queueCapacity);

        // Validate configuration
        if (threadPoolSize < 1) {threadPoolSize = 1;}
        if (queueCapacity < 128) {queueCapacity = 128;}
        if (batchSize < 16) {batchSize = 16;}
        if (batchSize > 512) {batchSize = 512;}
        if (flushFrequency < 1) {flushFrequency = 1;}
        if (flushFrequency > 20) {flushFrequency = 20;}

        if (enabled) {
            LeafConfig.LOGGER.info("Using queue-based packet sending with {} processor threads. " +
                    "Batch size: {}, Flush frequency: {} tick(s)",
                threadPoolSize, batchSize, flushFrequency);
        }
    }
}
