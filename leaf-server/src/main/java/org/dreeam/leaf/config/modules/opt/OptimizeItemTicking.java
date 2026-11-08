package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeItemTicking implements ConfigModule {

    @ConfigInfo(name = "only-tick-items-in-hand", comments = {"Whether to only tick / update items in main hand and offhand instead of the entire inventory.", "是否只对主手和副手中的物品进行 tick / 更新，而不是整个物品栏中的所有物品。"})
    public static boolean onlyTickItemsInHand = false;
}
