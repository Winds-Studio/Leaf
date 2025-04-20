package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class DespawnEndermanWhileHoldingMushroom extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.FIXES.getBaseKeyName();
    }

    public static boolean despawn = true;
    public static boolean noGrowthInTheEnd = true;

    @Override
    public void onLoaded() {
        despawn = config.getBoolean(getBasePath() + "mushroom.despawn-enderman-while-holding", despawn, config.pickStringRegionBased("""
                Endermen are capable of despawning even while holding a mushroom.""",
            """
                末影人拿着蘑菇时能够消失."""));
        noGrowthInTheEnd = config.getBoolean(getBasePath() + "mushroom.prevent-grow-in-the-end", noGrowthInTheEnd, config.pickStringRegionBased("""
                Prevent mushrooms from growing in the end.""",
            """
                阻止蘑菇在末地生长."""));
    }
}
