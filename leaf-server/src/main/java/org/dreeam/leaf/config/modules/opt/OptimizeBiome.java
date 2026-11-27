package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OptimizeBiome extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".cache-biome";
    }

    public static boolean enabled = false;
    public static boolean mobSpawn = false;
    public static boolean advancement = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        mobSpawn = globalConfig.getBoolean(basePath() + ".mob-spawning", false);
        advancement = globalConfig.getBoolean(basePath() + ".advancements", false);
    }
}
