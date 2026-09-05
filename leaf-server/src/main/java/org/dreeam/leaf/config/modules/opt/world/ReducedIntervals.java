package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "reduced-intervals")
public final class ReducedIntervals implements WorldConfigModule {

    @ConfigInfo(name = "check-stuck-in-wall")
    public int checkStuckInWall = 10;

    @ConfigInfo(name = "villager-item-repickup")
    public int villagerItemRepickup = 100;
}
