package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class DespawnTime extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".despawn-time";
    }

    @Experimental
    public static boolean proactiveWeakLoading = false;

    @Override
    public void onLoaded() {
        proactiveWeakLoading = config.getBoolean(getBasePath() + ".proactive-weak-loading-despawn", proactiveWeakLoading,
            config.pickStringRegionBased("""
                    Proactive despawn check for weak-loaded entities.
                    This is an experimental feature.""",
                """
                    启用主动弱加载实体消失检查，
                    这是一个实验性功能。"""));
    }
}
