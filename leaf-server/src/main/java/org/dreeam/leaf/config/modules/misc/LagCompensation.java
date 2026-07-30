package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class LagCompensation extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".lag-compensation";
    }

    public static boolean enabled = false;
    public static boolean enableForWater = false;
    public static boolean enableForLava = false;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                This section contains lag compensation features,
                which could ensure basic playing experience during a lag.""",
            """
                这部分包含滞后补偿功能,
                可以在卡顿情况下保障基本游戏体验.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        enableForWater = globalConfig.getBoolean(basePath() + ".enable-for-water", enableForWater);
        enableForLava = globalConfig.getBoolean(basePath() + ".enable-for-lava", enableForLava);
    }
}
