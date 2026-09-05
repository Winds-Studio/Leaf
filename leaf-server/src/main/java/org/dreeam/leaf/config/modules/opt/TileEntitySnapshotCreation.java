package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class TileEntitySnapshotCreation implements ConfigModule {

    @ConfigInfo(name = "create-snapshot-on-retrieving-blockstate")
    public static boolean enabled = true;
}
