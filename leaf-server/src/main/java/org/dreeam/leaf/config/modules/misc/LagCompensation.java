package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class LagCompensation extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".lag-compensation";
    }

    public static boolean enabled = false;
    public static boolean enableForLava = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                This section contains lag compensation features,
                which could ensure basic playing experience during a lag.""",
            """
                这部分包含滞后补偿功能,
                可以在卡顿情况下保障基本游戏体验.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        enableForLava = config.getBoolean(getBasePath() + ".enable-for-lava", enableForLava);
    }
}
