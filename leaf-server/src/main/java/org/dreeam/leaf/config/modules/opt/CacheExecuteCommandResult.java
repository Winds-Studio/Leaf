package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class CacheExecuteCommandResult extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".datapack";
    }

    @Experimental
    public static boolean cacheExecuteCommandResult = false;

    @Override
    public void onLoaded() {
        cacheExecuteCommandResult = config.getBoolean(getBasePath() + ".cache-execute-command-result", cacheExecuteCommandResult,
            config.pickStringRegionBased("""
                    *** Experimental Feature ***
                    Cache the result of same execute command in the current and next tick.
                    Will improve performance on servers with massive datapack functions.""",
                """
                    *** 实验性功能 ***
                    缓存当前和下一 tick 相同的 execute 命令结果.
                    将会提升有大量数据包函数的服务器性能."""));
    }
}
