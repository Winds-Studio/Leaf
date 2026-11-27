package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeEntityActivation extends ConfigModule {

    public String getBasePath() {
        return ConfigCategory.PERF.basePath() + ".optimize-entity-activation";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(getBasePath(), enabled);
    }
}
