package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public final class EntityWakeUpDurationRatio implements WorldConfigModule {

    @ConfigInfo(name = "entity-wake-up-duration-ratio-standard-deviation")
    public double ratioStandardDeviation = 0.2;
}
