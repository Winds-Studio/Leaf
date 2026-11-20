package org.dreeam.leaf.config.modules.misc.global;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "last-tick-time-in-tps-command")
public final class LastTickTimeInTpsCommand implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @ConfigInfo(name = "add-oversleep")
    public static boolean addOversleep = false;
}
