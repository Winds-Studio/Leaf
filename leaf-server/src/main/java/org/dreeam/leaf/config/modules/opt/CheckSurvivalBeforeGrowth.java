package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "check-survival-before-growth")
public class CheckSurvivalBeforeGrowth implements ConfigModule {

    @ConfigInfo(name = "cactus-check-survival", comments = {
        "Check if a cactus can survive before growing.",
        "在仙人掌生长前检查其是否能够存活。"
    })
    public static boolean cactusCheckSurvivalBeforeGrowth = false;
}
