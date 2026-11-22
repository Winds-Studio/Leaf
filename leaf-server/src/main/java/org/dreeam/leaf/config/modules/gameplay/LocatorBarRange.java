package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class LocatorBarRange implements ConfigModule {

    @ConfigInfo(name = "locator-bar-max-distance", comments = {
        """
            Maximum distance in blocks at which players show up on each other's
            locator bar.
            Set to 0 to keep vanilla behaviour.""",
        """
            玩家在彼此定位栏上显示的最大距离 (单位: 方块).
            设置为 0 保持原版行为."""
    })
    public static double maxDistance = 0.0D;
}
