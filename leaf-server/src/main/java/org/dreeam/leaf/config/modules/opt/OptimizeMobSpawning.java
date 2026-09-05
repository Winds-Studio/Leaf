package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeMobSpawning implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "optimize-mob-spawning")
    public static boolean enabled = false;
}
