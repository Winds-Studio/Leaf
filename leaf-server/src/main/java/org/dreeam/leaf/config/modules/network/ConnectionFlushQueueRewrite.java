package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ConnectionFlushQueueRewrite extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".connection-flush-queue-rewrite", enabled, config.pickStringRegionBased("""
                This replaces ConcurrentLinkedQueue with ArrayDeque for better performance
                and uses the Netty event loop to ensure thread safety.

                May increase the Netty thread usage and requires server restart to take effect
                Default: false
                """,
            """
                此选项将 ConcurrentLinkedQueue 替换为 ArrayDeque 以提高性能，
                并使用 Netty 事件循环以确保线程安全。

                默认值: false
                """));
    }
}
