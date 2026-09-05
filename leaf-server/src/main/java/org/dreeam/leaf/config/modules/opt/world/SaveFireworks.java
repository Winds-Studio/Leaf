package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "save-fireworks")
public final class SaveFireworks implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = true;
}
