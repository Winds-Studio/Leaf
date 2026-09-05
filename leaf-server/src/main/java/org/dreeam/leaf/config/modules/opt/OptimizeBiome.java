package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
@ConfigClassInfo(category = ConfigCategory.PERF, name = "cache-biome")
public class OptimizeBiome implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;
    @ConfigInfo(name = "mob-spawning")
    public static boolean mobSpawn = false;
    @ConfigInfo(name = "advancements")
    public static boolean advancement = false;
}
