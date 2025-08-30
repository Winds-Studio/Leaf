package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class EntityGoal extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".entity-goal";
    }

    public static int chanceTarget = -1; // all <= 10
    public static int chanceFollowParent = -1;
    public static int chanceAvoidEntity = -1;
    public static int chanceMoveThroughVillage = -1;
    public static int chanceTempt = -1;

    @Override
    public void onLoaded() {
        String path = getBasePath() + ".reciprocal-chance";
        chanceTarget = config.getInt(path + ".nearest_attackable_target", -1);
        chanceFollowParent = config.getInt(path + ".follow_parent", -1);
        chanceAvoidEntity = config.getInt(path + ".avoid_entity", -1);
        chanceMoveThroughVillage = config.getInt(path + ".move_through_village", -1);
        chanceTempt = config.getInt(path + ".temptation", -1);
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
        if (chanceMoveThroughVillage < 1) {
            chanceMoveThroughVillage = 1;
        } else {
            chanceMoveThroughVillage *= 2;
        }
        if (chanceTempt < 1) {
            chanceTempt = 1;
        } else {
            chanceTempt *= 2;
        }
    }
}
