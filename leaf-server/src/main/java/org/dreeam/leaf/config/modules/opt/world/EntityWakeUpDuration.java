package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "entity-wake-up-duration")
public final class EntityWakeUpDuration implements WorldConfigModule {

    @ConfigInfo(name = "ratio-standard-deviation")
    public double ratioStandardDeviation = 0.2;
}
