package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public final class SaveFireworks implements WorldConfigModule {

    @ConfigInfo(name = "save-fireworks")
    public boolean enabled = true;
}
