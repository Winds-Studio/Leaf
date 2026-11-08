package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC)
public class Including5sInGetTPS implements ConfigModule {

    @ConfigInfo(name = "including-5s-in-get-tps")
    public static boolean enabled = true;
}
