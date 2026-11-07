package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OptimizeTickPrecipitation extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".optimize-tick-precipitation";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath(), enabled, globalConfig.pickStringRegionBased("""
                Read the heightmap and biome of precipitation ticks from the ticking chunk directly,
                and skip ticks that have nothing to freeze while it is not raining.""",
            """
                降水刷新时直接从当前区块读取高度图和生物群系,
                并在未下雨且没有可结冰的水时跳过本次刷新."""));
    }
}
