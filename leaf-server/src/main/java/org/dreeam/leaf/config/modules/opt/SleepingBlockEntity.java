package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class SleepingBlockEntity extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean enabled = false;
    private static boolean sleepingBlockEntityInitialized;

    @Override
    public void onLoaded() {
        if (sleepingBlockEntityInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        sleepingBlockEntityInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".sleeping-block-entity", enabled);
    }
}
