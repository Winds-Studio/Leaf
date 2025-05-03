package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class SmoothTeleport extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".smooth-teleport";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled, config.pickStringRegionBased("""
                **Experimental feature**
                Whether to make a "smooth teleport" when players changing dimension.
                This requires original world and target world have same logical height to work.""",
            """
                **实验性功能**
                是否在玩家切换世界时尝试使用 "平滑传送".
                此项要求源世界和目标世界逻辑高度相同才会生效."""
        ));
    }
}
