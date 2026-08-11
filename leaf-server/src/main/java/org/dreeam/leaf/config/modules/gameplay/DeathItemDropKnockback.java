package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class DeathItemDropKnockback extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".death-item-drop-knockback";
    }

    public static boolean dropAround = true;
    public static double horizontalForce = 0.5;
    public static double verticalForce = 0.2;

    @Override
    public void onLoaded() {
        dropAround = globalConfig.getBoolean(basePath() + ".drop-around", dropAround,
            globalConfig.pickStringRegionBased(
                "If true, items will drop randomly around the player on death.",
                "如果为 “true”，物品会在玩家死亡时随机掉落在其周围."
            ));

        horizontalForce = globalConfig.getDouble(basePath() + ".horizontal-force", horizontalForce,
            globalConfig.pickStringRegionBased(
                "Base speed for horizontal velocity when randomly dropping items.",
                "随机掉落物品时水平速度的基本速度."
            ));

        verticalForce = globalConfig.getDouble(basePath() + ".vertical-force", verticalForce,
            globalConfig.pickStringRegionBased(
                "Upward motion for randomly dropped items.",
                "随机掉落物品的向上运动."
            ));
    }
}
