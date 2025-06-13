package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizeBlockEntities extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        // Transfer old config
        Boolean optimiseBlockEntities = config.getBoolean(getBasePath() + ".optimise-block-entities");
        if (optimiseBlockEntities != null && optimiseBlockEntities) {
            enabled =  true;
        }

        enabled = config.getBoolean(getBasePath() + ".optimize-block-entities", enabled);
    }
}
