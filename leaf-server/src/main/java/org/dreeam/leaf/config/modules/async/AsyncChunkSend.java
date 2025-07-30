package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncChunkSend extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-chunk-send";
    }

    public static boolean enabled = false;
    private static boolean asyncChunkSendInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Makes chunk packet preparation and sending asynchronous to improve server performance.
                This can significantly reduce main thread load when many players are loading chunks.""",
            """
                使区块数据包准备和发送异步化以提高服务器性能.
                当许多玩家同时加载区块时, 这可以显著减少主线程负载.""");

        if (asyncChunkSendInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncChunkSendInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
    }
}
