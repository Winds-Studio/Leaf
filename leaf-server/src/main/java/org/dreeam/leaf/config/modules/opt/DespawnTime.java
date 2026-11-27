package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

public class DespawnTime extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".despawn-time";
    }

    @Experimental
    @HotReloadUnsupported
    public static boolean proactiveWeakLoading = false;
    private static boolean loaded;

    @Override
    public void onLoaded() {
        boolean proactiveWeakLoadingConfig = globalConfig.getBoolean(basePath() + ".proactive-weak-loading-despawn", false,
            globalConfig.pickStringRegionBased("""
                    Proactive despawn check for weak-loaded entities.
                    This is an experimental feature.""",
                """
                    启用主动弱加载实体消失检查，
                    这是一个实验性功能。"""));

        if (!loaded) {
            proactiveWeakLoading = proactiveWeakLoadingConfig;
            loaded = true;
        }
    }
}
