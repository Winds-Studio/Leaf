package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeNoActionTime extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".optimize-no-action-time";
    }

    @Experimental
    public static boolean disableLightCheck = false;

    @Override
    public void onLoaded() {
        disableLightCheck = globalConfig.getBoolean(basePath() + ".disable-light-check", disableLightCheck);
    }
}
