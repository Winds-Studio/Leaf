package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class RemoveVanillaUsernameCheck extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".remove-vanilla-username-check";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled, config.pickStringRegionBased("""
                Remove Vanilla username check,
                allowing all characters as username.""",
            """
                移除原版的用户名验证,
                让所有字符均可作为玩家名."""));
    }
}
