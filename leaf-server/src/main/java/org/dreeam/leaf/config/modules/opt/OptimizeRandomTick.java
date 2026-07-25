package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeRandomTick extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".optimize-random-tick";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        Boolean old = globalConfig.getBoolean(ConfigCategory.PERF.basePath() + ".optimise-random-tick");
        if (old != null && old) {
            enabled = globalConfig.getBoolean(basePath(), true);
            return;
        }

        enabled = globalConfig.getBoolean(basePath(), enabled);
    }
}
