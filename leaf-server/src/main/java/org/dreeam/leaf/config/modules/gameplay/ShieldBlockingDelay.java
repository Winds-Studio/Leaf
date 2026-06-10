package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ShieldBlockingDelay extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".shield-blocking-delay";
    }

    public static int shieldBlockingDelay = 5;

    @Override
    public void onLoaded() {
        shieldBlockingDelay = config.getInt(getBasePath(), shieldBlockingDelay,
            config.pickStringRegionBased(
                "The delay in ticks before a raised shield blocks attacks.",
                "盾牌举起后可格挡攻击前的延迟（以刻为单位）。"
            ));
        if (shieldBlockingDelay < 0) {
            shieldBlockingDelay = 5;
        }
    }
}
