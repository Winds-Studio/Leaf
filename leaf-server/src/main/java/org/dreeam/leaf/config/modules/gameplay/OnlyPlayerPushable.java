package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OnlyPlayerPushable extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".only-player-pushable";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath(), enabled, globalConfig.pickStringRegionBased(
            "Enable to make only player pushable",
            "是否只允许玩家被实体推动"
        ));
    }
}
