package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "entity-goal")
public class EntityGoal implements ConfigModule {

    @ConfigInfo(name = "start-tick-chance.nearest-attackable-target")
    private static int configuredChanceTarget = -1;

    @ConfigInfo(name = "start-tick-chance.follow-parent")
    private static int configuredChanceFollowParent = -1;

    @ConfigInfo(name = "start-tick-chance.avoid-entity")
    private static int configuredChanceAvoidEntity = -1;

    @ConfigInfo(name = "start-tick-chance.temptation")
    private static int configuredChanceTempt = -1;

    @ConfigInfo(name = "start-tick-chance.enderman-look-for-player")
    private static int configuredChanceEndermanLookForPlayer = -1;

    public static @DoNotLoad int chanceTarget = -1; // only all <= 10
    public static @DoNotLoad int chanceFollowParent = -1;
    public static @DoNotLoad int chanceAvoidEntity = -1;
    public static @DoNotLoad int chanceTempt = -1;
    public static @DoNotLoad int chanceEndermanLookForPlayer = -1;

    @Override
    public void onLoaded() {
        chanceTarget = configuredChanceTarget;
        chanceFollowParent = configuredChanceFollowParent;
        chanceAvoidEntity = configuredChanceAvoidEntity;
        chanceTempt = configuredChanceTempt;
        chanceEndermanLookForPlayer = configuredChanceEndermanLookForPlayer;

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
