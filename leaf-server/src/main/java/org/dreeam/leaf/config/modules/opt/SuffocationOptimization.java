package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SuffocationOptimization extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".suffocation-optimization";
    }

    public static boolean enabled = true;
    private static boolean suffocationOptimizationInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Optimizes the suffocation check by selectively skipping
                the check in a way that still appears vanilla. This should
                be left enabled on most servers, but is provided as a
                configuration option if the vanilla deviation is undesirable.""",
            """
                通过选择性地跳过窒息检查来优化性能，同时保持与原版相似的行为。
                这在大多数服务器上应该保持启用，但如果不希望与原版有偏差，
                可以通过此配置选项禁用。""");

        if (suffocationOptimizationInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        suffocationOptimizationInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
    }
}