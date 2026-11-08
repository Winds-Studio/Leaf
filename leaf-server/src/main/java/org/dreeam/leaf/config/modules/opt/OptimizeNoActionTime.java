package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "optimize-no-action-time")
public class OptimizeNoActionTime implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "disable-light-check")
    public static boolean disableLightCheck = false;
}
