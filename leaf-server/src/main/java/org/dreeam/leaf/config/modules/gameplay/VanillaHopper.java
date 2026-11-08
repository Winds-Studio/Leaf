package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class VanillaHopper implements ConfigModule {

    @ConfigInfo(name = "use-vanilla-hopper")
    public static boolean enabled = false;
}
