package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeNoActionTime extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-no-action-time";
    }

    @Experimental
    public static boolean disableLightCheck = false;

    @Override
    public void onLoaded() {
        disableLightCheck = config.getBoolean(getBasePath() + ".disable-light-check", disableLightCheck);
    }
}
