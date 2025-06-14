package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class AsyncContainerBroadcast extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-container-broadcast";
    }

    public static boolean enabled = false;
    public static int minThreads = 1;
    public static int maxThreads = 2;
    public static int keepalive = 30;
    public static boolean enableBackpressureHandling = true;
    public static boolean enableBatchedSending = true;
    private static boolean asyncContainerBroadcastInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Asynchronous container network synchronization, reducing main thread blocking.
                Uses dedicated threads for packet preparation while keeping network I/O on event loops.""",
            """
                异步化容器网络同步，减少主线程阻塞.
                使用专用线程进行数据包准备，同时在事件循环中保持网络 I/O.""");

        if (asyncContainerBroadcastInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncContainerBroadcastInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        minThreads = config.getInt(getBasePath() + ".min-threads", minThreads);
        maxThreads = config.getInt(getBasePath() + ".max-threads", maxThreads);
        keepalive = config.getInt(getBasePath() + ".keepalive", keepalive);
        enableBackpressureHandling = config.getBoolean(getBasePath() + ".enable-backpressure-handling", enableBackpressureHandling);
        enableBatchedSending = config.getBoolean(getBasePath() + ".enable-batched-sending", enableBatchedSending);

        if (minThreads <= 0) minThreads = 1;
        if (maxThreads <= 0) maxThreads = 2;
        if (maxThreads < minThreads) maxThreads = minThreads;
        if (keepalive <= 0) keepalive = 30;

        if (!enabled) {
            minThreads = 0;
            maxThreads = 0;
        } else {
            LeafConfig.LOGGER.info("Using {}-{} threads for Async Container Broadcast", minThreads, maxThreads);
            org.dreeam.leaf.async.container.AsyncContainerBroadcaster.init();
        }
    }
}
