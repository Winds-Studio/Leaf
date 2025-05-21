package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class DeathItemDropKnockback extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".death-item-drop-knockback";
    }

    public static boolean dropAround = true;
    public static double horizontalForce = 0.5;
    public static double verticalForce = 0.2;

    @Override
    public void onLoaded() {
        dropAround = config.getBoolean(getBasePath() + ".drop-around", dropAround,
            config.pickStringRegionBased(
                "If true, items will drop randomly around the player on death.",
                "如果为 “true”，物品会在玩家死亡时随机掉落在其周围."
            ));

        horizontalForce = config.getDouble(getBasePath() + ".horizontal-force", horizontalForce,
            config.pickStringRegionBased(
                "Base speed for horizontal velocity when randomly dropping items.",
                "随机掉落物品时水平速度的基本速度."
            ));

        verticalForce = config.getDouble(getBasePath() + ".vertical-force", verticalForce,
            config.pickStringRegionBased(
                "Upward motion for randomly dropped items.",
                "随机掉落物品的向上运动."
            ));
    }
}
