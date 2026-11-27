package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class SkipInactiveEntityForExecute extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".datapack";
    }

    public static boolean skipInactiveEntityForExecute = false;

    @Override
    public void onLoaded() {
        skipInactiveEntityForExecute = globalConfig.getBoolean(basePath() + ".skip-inactive-entity-for-execute-command", skipInactiveEntityForExecute,
            globalConfig.pickStringRegionBased("""
                    Skip selecting inactive entities when using execute command.
                    Will improve performance on servers with massive datapack functions.""",
                """
                    execute 命令执行时跳过不活跃实体.
                    将会提升有大量数据包函数的服务器性能."""));
    }
}
