package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class EntityGoal extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".entity-goal";
    }

    public static int chanceTarget = -1; // only all <= 10
    public static int chanceFollowParent = -1;
    public static int chanceAvoidEntity = -1;
    public static int chanceTempt = -1;
    public static int chanceEndermanLookForPlayer = -1;

    @Override
    public void onLoaded() {
        String path = getBasePath() + ".start-tick-chance";
        chanceTarget = config.getInt(path + ".nearest-attackable-target", -1);
        chanceFollowParent = config.getInt(path + ".follow-parent", -1);
        chanceAvoidEntity = config.getInt(path + ".avoid-entity", -1);
        chanceTempt = config.getInt(path + ".temptation", -1);
        chanceEndermanLookForPlayer = config.getInt(path + ".enderman-look-for-player", -1);

        // expect nearest_attackable_target
        if (chanceFollowParent < 1) {
            chanceFollowParent = 1;
        } else {
            chanceFollowParent *= 2;
        }
        if (chanceAvoidEntity < 1) {
            chanceAvoidEntity = 1;
        } else {
            chanceAvoidEntity *= 2;
        }
        if (chanceTempt < 1) {
            chanceTempt = 1;
        } else {
            chanceTempt *= 2;
        }
        if (chanceEndermanLookForPlayer < 1) {
            chanceEndermanLookForPlayer = 1;
        } else {
            chanceEndermanLookForPlayer *= 2;
        }
    }
}
