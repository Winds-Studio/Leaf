package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class TheEndBiomeCache extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".c2me.end-biome-cache";
    }

    public static boolean enabled = false;
    public static int cacheCapacity = 1024;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Enables C2ME's The End biome cache, which accelerates The End world generation.""",
            """
                启用 C2ME 末地生物群系缓存, 加速末地地形生成.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        cacheCapacity = globalConfig.getInt(basePath() + ".cache-capacity", cacheCapacity);
    }
}
