package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OptimizePlayerMovementProcessing extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".optimize-player-movement", enabled, globalConfig.pickStringRegionBased("""
                Whether to optimize player movement processing by skipping unnecessary edge checks and avoiding redundant view distance updates.""",
            """
                是否优化玩家移动处理，跳过不必要的边缘检查并避免冗余的视距更新。"""));
    }
}
