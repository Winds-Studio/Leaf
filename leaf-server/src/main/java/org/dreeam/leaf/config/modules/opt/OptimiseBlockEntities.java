package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimiseBlockEntities extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".optimise-block-entities", enabled, config.pickStringRegionBased(
            """
                Use fastutil's Object2ObjectOpenHashMap for ticking BlockEntities
                instead of the standard HashMap.""",
            """
                使用 fastutil 的 Object2ObjectOpenHashMap
                替代标准 HashMap 优化 BlockEntities"""));
    }
}
