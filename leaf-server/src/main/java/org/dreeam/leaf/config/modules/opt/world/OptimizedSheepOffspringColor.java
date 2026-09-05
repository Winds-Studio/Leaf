package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "optimized-sheep-offspring-color")
public final class OptimizedSheepOffspringColor implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = true;
}
