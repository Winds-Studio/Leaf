package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class OptimizeSelector extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".datapack";
    }

    public static boolean entitySelectorOptimizations = true;

    @Override
    public void onLoaded() {
        entitySelectorOptimizations = globalConfig.getBoolean(basePath() + ".entity-selector-optimizations", entitySelectorOptimizations);
    }
}
