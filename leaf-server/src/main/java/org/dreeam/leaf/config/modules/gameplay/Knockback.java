package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class Knockback extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".knockback";
    }

    public static boolean snowballCanKnockback = false;
    public static boolean eggCanKnockback = false;
    public static boolean canPlayerKnockbackZombie = true;
    @Experimental
    public static boolean flushKnockback = false;
    public static boolean oldBlastProtectionKnockbackBehavior = false;

    @Override
    public void onLoaded() {
        snowballCanKnockback = globalConfig.getBoolean(basePath() + ".snowball-knockback-players", snowballCanKnockback,
            globalConfig.pickStringRegionBased(
                "Make snowball can knockback players.",
                "使雪球可以击退玩家."
            ));
        eggCanKnockback = globalConfig.getBoolean(basePath() + ".egg-knockback-players", eggCanKnockback,
            globalConfig.pickStringRegionBased(
                "Make egg can knockback players.",
                "使鸡蛋可以击退玩家."
            ));
        canPlayerKnockbackZombie = globalConfig.getBoolean(basePath() + ".can-player-knockback-zombie", canPlayerKnockbackZombie,
            globalConfig.pickStringRegionBased(
                "Make players can knockback zombie.",
                "使玩家可以击退僵尸."
            ));
        flushKnockback = globalConfig.getBoolean(basePath() + ".flush-location-while-knockback-player", flushKnockback,
            globalConfig.pickStringRegionBased(
                "Synchronize player immediately when knocked back.",
                "被击退时立即同步玩家."
            ));
        oldBlastProtectionKnockbackBehavior = globalConfig.getBoolean(basePath() + ".old-blast-protection-explosion-knockback", oldBlastProtectionKnockbackBehavior);
    }
}
