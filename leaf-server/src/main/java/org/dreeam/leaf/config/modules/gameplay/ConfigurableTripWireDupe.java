package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ConfigurableTripWireDupe extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".allow-tripwire-dupe", enabled, config.pickStringRegionBased("Enables or disables the configurable trip wire dupe feature.",
            "启用或禁用绊线复制功能。"));
    }
}
