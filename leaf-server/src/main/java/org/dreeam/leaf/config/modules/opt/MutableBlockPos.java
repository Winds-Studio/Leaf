package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class MutableBlockPos extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".use-mutable-blockpos", enabled,
            config.pickStringRegionBased(
                """
                    Reuse BlockPos to reduce memory allocation and improve tick performance.
                    May conflict with certain plugins or operations. Disable if position issues occur.""",
                """
                    复用 BlockPos 减少内存分配，提升 tick 性能.
                    可能与某些插件或操作冲突，如出现位置异常请关闭."""));
    }
}
