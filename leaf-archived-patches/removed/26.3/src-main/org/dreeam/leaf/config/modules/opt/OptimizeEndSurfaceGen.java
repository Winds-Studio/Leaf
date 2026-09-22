package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class OptimizeEndSurfaceGen extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".optimize-end-surface-gen";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled, globalConfig.pickStringRegionBased("""
                Skip trivial End surface rule build process.
                May be incompatible with some datapacks that modify End world generation.""",
            """
                跳过不必要的末地 surface rule 构建。
                可能不兼容一些修改末地世界生成的数据包。"""));
    }
}
