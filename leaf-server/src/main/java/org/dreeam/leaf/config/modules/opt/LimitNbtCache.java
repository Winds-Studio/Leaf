package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class LimitNbtCache extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".c2me.limit-nbt-cache";
    }

    public static long chunkDataCacheSoftLimit = 8192L;
    public static long chunkDataCacheLimit = 32678L;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Limits NBT chunk data write cache to avoid memory retention during massive chunk operations.""",
            """
                限制 NBT 区块数据写入缓存, 避免大批量区块保存时内存占用过多.""");

        chunkDataCacheSoftLimit = globalConfig.getLong(basePath() + ".chunk-data-cache-soft-limit", chunkDataCacheSoftLimit);
        chunkDataCacheLimit = globalConfig.getLong(basePath() + ".chunk-data-cache-limit", chunkDataCacheLimit);
    }
}
