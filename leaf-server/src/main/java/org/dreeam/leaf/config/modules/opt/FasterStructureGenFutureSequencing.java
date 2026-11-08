package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class FasterStructureGenFutureSequencing implements ConfigModule {

    @ConfigInfo(name = "faster-structure-gen-future-sequencing", comments = {
        "May cause the inconsistent order of future compose tasks.",
        "更快的结构生成任务分段."
    })
    public static boolean enabled = true;
}
