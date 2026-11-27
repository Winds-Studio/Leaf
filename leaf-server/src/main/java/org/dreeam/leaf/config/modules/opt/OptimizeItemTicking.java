package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OptimizeItemTicking extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean onlyTickItemsInHand = false;

    @Override
    public void onLoaded() {
        onlyTickItemsInHand = globalConfig.getBoolean(basePath() + ".only-tick-items-in-hand", onlyTickItemsInHand, globalConfig.pickStringRegionBased("""
                Whether to only tick / update items in main hand and offhand instead of the entire inventory.""",
            """
                是否只对主手和副手中的物品进行 tick / 更新，而不是整个物品栏中的所有物品。"""));
    }
}
