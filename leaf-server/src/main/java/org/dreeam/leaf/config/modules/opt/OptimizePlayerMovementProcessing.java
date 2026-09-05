package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizePlayerMovementProcessing implements ConfigModule {

    @ConfigInfo(name = "optimize-player-movement", comments = {"Whether to optimize player movement processing by skipping unnecessary edge checks and avoiding redundant view distance updates.", "是否优化玩家移动处理，跳过不必要的边缘检查并避免冗余的视距更新。"})
    public static boolean enabled = true;
}
