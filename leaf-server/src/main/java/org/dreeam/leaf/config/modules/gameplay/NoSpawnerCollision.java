package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class NoSpawnerCollision extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".disable-mob-collision-from-spawner";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled, config.pickStringRegionBased(
            "Enable to disable collision for mobs spawned from spawners",
            "是否禁用从刷怪笼生成的生物与其他实体的碰撞"
        ));
    }
}
