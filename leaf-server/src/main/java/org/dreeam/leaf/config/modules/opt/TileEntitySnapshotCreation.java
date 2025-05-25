package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class TileEntitySnapshotCreation extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".create-snapshot-on-retrieving-blockstate", enabled, config.pickStringRegionBased("""
                Enables snapshot creation when retrieving block state.
                This can improve performance by reducing redundant calculations.""",
            """
                是否在获取方块状态时创建快照.
                通过减少重复计算提升性能。"""));
    }
}
