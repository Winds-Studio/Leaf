package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public final class OptimizedSheepOffspringColor implements WorldConfigModule {

    @ConfigInfo(name = "use-optimized-sheep-offspring-color")
    public boolean enabled = true;
}
