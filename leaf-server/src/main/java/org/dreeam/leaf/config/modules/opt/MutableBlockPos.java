package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class MutableBlockPos implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "reuse-random-ticking-blockpos", comments = {"Experimental feature.\nReuse BlockPos to reduce memory allocation slightly and improve performance on random ticking.\nMay conflict with certain plugins or operations. Disable if position issues occur.", "实验性功能\n复用 BlockPos 以略微减少内存分配，提升 random ticking 的性能.\n可能与某些插件或操作冲突，如出现位置异常请关闭."})
    public static boolean enabled = false;
}
