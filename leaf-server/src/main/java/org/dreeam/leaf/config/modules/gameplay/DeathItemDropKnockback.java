package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "death-item-drop-knockback")
public class DeathItemDropKnockback implements ConfigModule {

    @ConfigInfo(name = "drop-around", comments = {
        "If true, items will drop randomly around the player on death.",
        "如果为 “true”，物品会在玩家死亡时随机掉落在其周围."
    })
    public static boolean dropAround = true;

    @ConfigInfo(name = "horizontal-force", comments = {
        "Base speed for horizontal velocity when randomly dropping items.",
        "随机掉落物品时水平速度的基本速度."
    })
    public static double horizontalForce = 0.5;

    @ConfigInfo(name = "vertical-force", comments = {
        "Upward motion for randomly dropped items.",
        "随机掉落物品的向上运动."
    })
    public static double verticalForce = 0.2;
}
