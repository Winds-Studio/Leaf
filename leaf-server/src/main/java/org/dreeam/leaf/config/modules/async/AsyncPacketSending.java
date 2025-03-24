package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class AsyncPacketSending extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-packet-sending";
    }

    @Experimental
    public static boolean enabled = false;

    public static int threadPoolSize = 4;
    public static int queueCapacity = 4096;
    public static boolean prioritizeMovementPackets = true;
    public static boolean prioritizeChatPackets = true;
    public static boolean spinWaitForReadyPackets = true;
    public static long spinTimeNanos = 1000; // 1 microsecond
    public static boolean batchProcessing = true;
    public static int batchSize = 128;

    private static boolean asyncPacketSendingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                **Experimental feature**
                This moves packet sending operations to background threads, reducing main thread load.
                Can significantly improve performance on high-player-count servers.""",
            """
                这将数据包发送操作移至后台线程，减少主线程负载。
                在高玩家数量的服务器上可以显著提高性能。""");

        if (!asyncPacketSendingInitialized) {
            asyncPacketSendingInitialized = true;
            enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
            threadPoolSize = config.getInt(getBasePath() + ".thread-pool-size", threadPoolSize);
            queueCapacity = config.getInt(getBasePath() + ".queue-capacity", queueCapacity);
            prioritizeMovementPackets = config.getBoolean(getBasePath() + ".prioritize-movement-packets", prioritizeMovementPackets);
            prioritizeChatPackets = config.getBoolean(getBasePath() + ".prioritize-chat-packets", prioritizeChatPackets);
            spinWaitForReadyPackets = config.getBoolean(getBasePath() + ".spin-wait-for-ready-packets", spinWaitForReadyPackets);
            spinTimeNanos = config.getLong(getBasePath() + ".spin-time-nanos", spinTimeNanos);
            batchProcessing = config.getBoolean(getBasePath() + ".batch-processing", batchProcessing);
            batchSize = config.getInt(getBasePath() + ".batch-size", batchSize);
        }
    }
}
