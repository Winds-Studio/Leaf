package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "entity-wake-up-duration")
public final class EntityWakeUpDuration implements WorldConfigModule {

    @ConfigInfo(name = "ratio-standard-deviation")
    public double ratioStandardDeviation = 0.2;
}
