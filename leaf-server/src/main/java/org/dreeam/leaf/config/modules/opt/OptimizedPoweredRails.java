package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizedPoweredRails implements ConfigModule {

    @ConfigInfo(name = "optimized-powered-rails", comments = {"Whether to use optimized powered rails.\nThe implementation is based on RailOptimization made by GitHub@FxMorin", "是否使用铁轨优化。\n优化实现基于 GitHub@FxMori 的 RailOptimization 模组。"})
    public static boolean enabled = false;
}
