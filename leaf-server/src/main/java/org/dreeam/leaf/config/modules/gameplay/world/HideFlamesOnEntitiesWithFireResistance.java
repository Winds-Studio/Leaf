package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "hide-flames-on-entities-with-fire-resistance")
public final class HideFlamesOnEntitiesWithFireResistance implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = false;
}
