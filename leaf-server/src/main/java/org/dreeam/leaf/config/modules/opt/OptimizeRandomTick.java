package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeRandomTick extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-random-tick";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        Boolean old = config.getBoolean(EnumConfigCategory.PERF.getBaseKeyName() + ".optimise-random-tick");
        if (old != null && old) {
            enabled = config.getBoolean(getBasePath(), true);
            return;
        }

        enabled = config.getBoolean(getBasePath(), enabled);
    }
}
