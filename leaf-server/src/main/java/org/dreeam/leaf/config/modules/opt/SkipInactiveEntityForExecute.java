package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SkipInactiveEntityForExecute extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean skipInactiveEntityForExecute = false;

    @Override
    public void onLoaded() {
        skipInactiveEntityForExecute = config.getBoolean(getBasePath() + ".skip-inactive-entity-for-execute-command", skipInactiveEntityForExecute,
            config.pickStringRegionBased("""
                    *** Experimental Feature ***
                    Skip selecting inactive entities when using execute command.
                    Will improve performance on servers with massive datapack functions.""",
                """
                    *** 实验性功能 ***
                    execute 命令执行时跳过不活跃实体.
                    将会提升有大量数据包函数的服务器性能."""));
    }
}
