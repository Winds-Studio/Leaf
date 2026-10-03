package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class AsyncChunkSending extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-chunk-sending";
    }

    public static boolean enabled = true;
    public static int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Snapshots chunk sections and serializes chunk packets asynchronously off the tick thread.""",
            """
                在主线程只拷贝区块段快照, 异步多线程执行区块封包序列化, 提高发包吞吐量.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        maxThreads = globalConfig.getInt(basePath() + ".threads", maxThreads);
    }
}
