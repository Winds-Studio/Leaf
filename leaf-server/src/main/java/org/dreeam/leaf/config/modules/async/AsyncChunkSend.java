package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class AsyncChunkSend extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-chunk-send";
    }

    public static boolean enabled = false;
    private static boolean asyncChunkSendInitialized;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Makes chunk packet preparation and sending asynchronous to improve server performance.
                This can significantly reduce main thread load when many players are loading chunks.""",
            """
                使区块数据包准备和发送异步化以提高服务器性能.
                当许多玩家同时加载区块时, 这可以显著减少主线程负载.""");

        if (asyncChunkSendInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        asyncChunkSendInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
