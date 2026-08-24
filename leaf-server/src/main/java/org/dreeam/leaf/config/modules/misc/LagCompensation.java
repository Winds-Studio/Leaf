package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "lag-compensation", comments = {
    """
        This section contains lag compensation features,
        which could ensure basic playing experience during a lag.""",
    """
        这部分包含滞后补偿功能,
        可以在卡顿情况下保障基本游戏体验."""
})
public class LagCompensation implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @ConfigInfo(name = "enable-for-water")
    public static boolean enableForWater = false;

    @ConfigInfo(name = "enable-for-lava")
    public static boolean enableForLava = false;
}
