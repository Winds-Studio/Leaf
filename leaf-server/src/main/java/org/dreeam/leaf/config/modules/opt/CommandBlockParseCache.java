package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class CommandBlockParseCache extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".command-block-parse-results-caching";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Caches command block parse results to significantly reduce performance overhead.""",
            """
                缓存命令方块的解析结果, 显著减少循环命令方块的性能损耗.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
