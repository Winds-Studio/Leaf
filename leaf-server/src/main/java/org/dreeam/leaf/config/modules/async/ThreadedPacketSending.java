package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadedPacketSending extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".threaded-packet-sending";
    }

    public static boolean enabled = false;
    public static int threadPoolSize = 2;
    public static int queueCapacity = 1024;
    private static ExecutorService PACKET_THREAD_EXECUTOR = null;

    public static ExecutorService getPacketThreadExecutor() {
        if (PACKET_THREAD_EXECUTOR == null && enabled) {
            PACKET_THREAD_EXECUTOR = new ThreadPoolExecutor(
                threadPoolSize, // Core pool size
                threadPoolSize, // Max pool size (same as core to prevent growing)
                60L, TimeUnit.SECONDS, // Keepalive time
                new LinkedBlockingQueue<>(queueCapacity), // Using bounded queue to prevent memory issues
                new ThreadFactoryBuilder()
                    .setNameFormat("Packet-Thread-%d")
                    .setDaemon(true)
                    .setUncaughtExceptionHandler((t, e) -> {
                        LeafConfig.LOGGER.error("Uncaught exception in Packet Thread", e);
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
                    LeafConfig.LOGGER.warn("Packet thread pool did not terminate in time");
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
                Offloads packet sending operations to a dedicated thread pool to reduce main thread load.
                This can significantly improve performance for busy servers where network operations
                create contention on the main thread.""",
            """
                将数据包发送操作转移到专用线程池，以减少主线程负载。
                对于网络操作在主线程上造成争用的繁忙服务器，这可以显著提高性能。""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        threadPoolSize = config.getInt(getBasePath() + ".thread-pool-size", threadPoolSize);
        queueCapacity = config.getInt(getBasePath() + ".queue-capacity", queueCapacity);

        // Validate thread pool size
        if (threadPoolSize < 1) {threadPoolSize = 1;}

        // Ensure queue capacity is reasonable
        if (queueCapacity < 128) {queueCapacity = 128;}

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for threaded packet sending with queue capacity {}",
                threadPoolSize, queueCapacity);
        }
    }
}
