package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class Knockback extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".knockback";
    }

    public static boolean snowballCanKnockback = false;
    public static boolean eggCanKnockback = false;
    public static boolean canPlayerKnockbackZombie = true;
    @Experimental
    public static boolean flushKnockback = false;

    @Override
    public void onLoaded() {
        snowballCanKnockback = config.getBoolean(getBasePath() + ".snowball-knockback-players", snowballCanKnockback,
            config.pickStringRegionBased(
                "Make snowball can knockback players.",
                "使雪球可以击退玩家."
            ));
        eggCanKnockback = config.getBoolean(getBasePath() + ".egg-knockback-players", eggCanKnockback,
            config.pickStringRegionBased(
                "Make egg can knockback players.",
                "使鸡蛋可以击退玩家."
            ));
        canPlayerKnockbackZombie = config.getBoolean(getBasePath() + ".can-player-knockback-zombie", canPlayerKnockbackZombie,
            config.pickStringRegionBased(
                "Make players can knockback zombie.",
                "使玩家可以击退僵尸."
            ));
        flushKnockback = config.getBoolean(getBasePath() + ".flush-location-while-knockback-player", flushKnockback);
    }
}
