package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class WoolHopperCounter extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".wool-hopper-counter";
    }

    public static boolean enabled = false;
    public static boolean unlimited = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        unlimited = config.getBoolean(getBasePath() + "unlimited-speed", unlimited);
    }
}
