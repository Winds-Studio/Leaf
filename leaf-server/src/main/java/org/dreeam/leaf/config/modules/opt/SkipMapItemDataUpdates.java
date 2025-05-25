package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SkipMapItemDataUpdates extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer", enabled, config.pickStringRegionBased( "Skip certain MapItemData updates to reduce performance overhead.", "跳过部分地图数据更新以减少性能开销。"));
    }
}
