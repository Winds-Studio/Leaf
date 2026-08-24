package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "optimized-sheep-offspring-color")
public final class OptimizedSheepOffspringColor implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = true;
}
