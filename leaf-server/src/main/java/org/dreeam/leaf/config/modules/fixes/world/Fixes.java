package org.dreeam.leaf.config.modules.fixes.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.FIXES, name = "gameplay-fixes")
public final class Fixes implements WorldConfigModule {

    @ConfigInfo(name = "broadcast-crit-animations-as-the-entity-being-critted")
    public boolean broadcastCritAnimationsAsTheEntityBeingCritted = false;

    @ConfigInfo(name = "mc-238526")
    public boolean mc238526 = false;

    @ConfigInfo(name = "mc-121706")
    public boolean mc121706 = false;
}
