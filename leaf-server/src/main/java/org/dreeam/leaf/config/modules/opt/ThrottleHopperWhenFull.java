package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ThrottleHopperWhenFull extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".throttle-hopper-when-full";
    }

    public static boolean enabled = false;
    public static int skipTicks = 8;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled, config.pickStringRegionBased("""
                Throttles the hopper if target container is full.""",
            """
                是否在目标容器已满时阻塞漏斗."""));
        skipTicks = config.getInt(getBasePath() + ".skip-ticks", skipTicks, config.pickStringRegionBased("""
                How many ticks to throttle when the Hopper is throttled.""",
            """
                每次阻塞多少 tick."""));
    }
}
