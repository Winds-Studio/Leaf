package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizedPoweredRails extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimized-powered-rails";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled,
            config.pickStringRegionBased(
                """
                    Whether to use optimized powered rails.
                    The implementation is based on RailOptimization made by GitHub@FxMorin""",
                """
                    是否使用铁轨优化。
                    优化实现基于 GitHub@FxMori 的 RailOptimization 模组。"""));
    }
}
