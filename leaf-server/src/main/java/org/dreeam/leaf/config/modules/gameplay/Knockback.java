package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class Knockback extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".knockback";
    }

    public static boolean snowballCanKnockback = false;
    public static boolean eggCanKnockback = false;
    public static boolean canPlayerKnockbackZombie = true;
    public static boolean oldBlastProtectionKnockbackBehavior = false;
    public static boolean useLegacyTrackerTicking = false;

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
        oldBlastProtectionKnockbackBehavior = globalConfig.getBoolean(basePath() + ".old-blast-protection-explosion-knockback", oldBlastProtectionKnockbackBehavior);
        useLegacyTrackerTicking =  globalConfig.getBoolean(basePath() + ".use-legacy-tracker-ticking", useLegacyTrackerTicking);
    }
}
