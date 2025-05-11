package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizeItemTicking extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean onlyTickItemsInHand = false;

    @Override
    public void onLoaded() {
        onlyTickItemsInHand = config.getBoolean(getBasePath() + ".only-tick-items-in-hand", onlyTickItemsInHand, config.pickStringRegionBased("""
                Whether to only tick/update items in main hand and offhand instead of the entire inventory.""",
            """
                是否只对主手和副手中的物品进行tick/更新，而不是整个物品栏中的所有物品。"""));
    }
}
