package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ReduceChunkSourceUpdates extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".reduce-chunk-source-updates";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled,
            config.pickStringRegionBased(
                "Reduces chunk source updates on inter-chunk player moves. (Recommended to enable)",
                "减少玩家跨区块移动时的区块源更新。"
            )
        );
    }
}
