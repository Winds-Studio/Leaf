package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AlternativeJoin extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".async-switch-state", enabled, config.pickStringRegionBased(
            "Async switch connection state.",
            "异步切换连接状态."));
    }
}
