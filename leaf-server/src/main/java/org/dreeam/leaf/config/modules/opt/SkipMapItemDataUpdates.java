package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class SkipMapItemDataUpdates implements ConfigModule {

    @ConfigInfo(name = "skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer")
    public static boolean enabled = true;
}
