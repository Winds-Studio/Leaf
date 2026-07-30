package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class MutableBlockPos extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    @Experimental
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".reuse-random-ticking-blockpos", enabled,
            globalConfig.pickStringRegionBased(
                """
                    Experimental feature.
                    Reuse BlockPos to reduce memory allocation slightly and improve performance on random ticking.
                    May conflict with certain plugins or operations. Disable if position issues occur.""",
                """
                    实验性功能
                    复用 BlockPos 以略微减少内存分配，提升 random ticking 的性能.
                    可能与某些插件或操作冲突，如出现位置异常请关闭."""));
    }
}
