package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.dreeam.leaf.util.LeafConstants;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeDespawn implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "optimize-mob-despawn")
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        if (enabled) {
            if (!LeafConstants.ENABLE_FMA) {
                LeafConfig.LOGGER.info("NOTE: Recommend enabling FMA to work with optimize-mob-despawn.");
            }
        }
    }
}
