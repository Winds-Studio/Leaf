package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class MC298464 extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.FIXES.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".fix-mc298464", enabled);
    }
}
