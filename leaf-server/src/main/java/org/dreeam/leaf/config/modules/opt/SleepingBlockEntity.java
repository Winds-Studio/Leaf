package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.PERF)
public class SleepingBlockEntity implements ConfigModule {

    @ConfigInfo(name = "sleeping-block-entity")
    public static boolean enabled = false;
}
