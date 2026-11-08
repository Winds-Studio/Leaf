package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class ConfigurableTripWireDupe implements ConfigModule {

    @ConfigInfo(name = "allow-tripwire-dupe")
    public static boolean enabled = false;
}
