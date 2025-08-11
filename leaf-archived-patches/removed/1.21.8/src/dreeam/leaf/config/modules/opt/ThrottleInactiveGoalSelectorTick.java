package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ThrottleInactiveGoalSelectorTick extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".inactive-goal-selector-throttle", enabled, config.pickStringRegionBased("""
                Throttles the AI goal selector in entity inactive ticks.
                This can improve performance by a few percent, but has minor gameplay implications.""",
            """
                是否在实体不活跃 tick 时阻塞 AI 目标选择器.
                有助于提升性能, 但对游戏有轻微影响."""));
    }
}
