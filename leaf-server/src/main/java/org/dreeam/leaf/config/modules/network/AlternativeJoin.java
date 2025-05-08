package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AlternativeJoin extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName();
    }

    public static boolean AlternativeJoin = true;

    @Override
    public void onLoaded() {
        AlternativeJoin = config.getBoolean(getBasePath() + ".alternative-join", AlternativeJoin, config.pickStringRegionBased(
            "Use alternative login logic to skip synchronization.",
            "使用替代登录逻辑以跳过同步。"));
    }
}
