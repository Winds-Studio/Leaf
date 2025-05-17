package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class EyeFluidCache extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".cache-eye-fluid-status", enabled,
            config.pickStringRegionBased(
                "Whether to cache the isEyeInFluid method to improve performance and reduce memory usage.",
                "是否为 isEyeInFluid 方法启用缓存，以优化性能并减少内存使用."));
    }
}
