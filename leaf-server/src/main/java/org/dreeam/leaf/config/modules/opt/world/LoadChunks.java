package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "load-chunks")
public final class LoadChunks implements WorldConfigModule {

    @ConfigInfo(name = "to-spawn-phantoms")
    public boolean toSpawnPhantoms = false;

    @ConfigInfo(name = "to-activate-climbing-entities")
    public boolean toActivateClimbingEntities = false;
}
