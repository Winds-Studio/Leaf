package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class ReduceChunkSourceUpdates extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".reduce-chunk-source-updates";
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                *** Experimental Feature ***
                Reduces chunk source updates on inter-chunk player moves.""",
            """
                *** 实验性功能 ***
                减少玩家跨区块移动时的区块源更新."""
        );
        enabled = config.getBoolean(getBasePath() + ".force-enabled", enabled);
        if (!enabled && config.getBoolean(getBasePath() + ".enabled", enabled)) {
            LOGGER.warn("Disabled reduce-chunk-source-updates due to experimentation");
        }
    }
}
