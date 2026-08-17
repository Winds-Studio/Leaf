package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "async-chunk-send", comments = {
    """
        Makes chunk packet preparation and sending asynchronous to improve server performance.
        This can significantly reduce main thread load when many players are loading chunks.""",
    """
        使区块数据包准备和发送异步化以提高服务器性能.
        当许多玩家同时加载区块时, 这可以显著减少主线程负载."""
})
public class AsyncChunkSend implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;
}
