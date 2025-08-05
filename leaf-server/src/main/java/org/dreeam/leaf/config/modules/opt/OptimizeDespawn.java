package org.dreeam.leaf.config.modules.opt;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeDespawn extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-mob-despawn";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled);
        if (enabled) {
            gg.pufferfish.pufferfish.simd.SIMDDetection.initialize();
            if (!Boolean.getBoolean("Leaf.enableFMA") || !SIMDDetection.isEnabled()) {
                LOGGER.info("NOTE: Recommend enabling FMA and Vector API to work with optimize-mob-despawn.");
            }
        }
    }
}
