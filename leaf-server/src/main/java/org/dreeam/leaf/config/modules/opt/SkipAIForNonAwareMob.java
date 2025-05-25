package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SkipAIForNonAwareMob extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".skip-ai-for-non-aware-mob", enabled, config.pickStringRegionBased( "Skip AI for mobs without awareness or valid AI to improve performance.", "跳过无感知或无效 AI 的生物以提升性能。"));
    }
}
