package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
@ConfigClassInfo(category = ConfigCategory.PERF, name = "datapack")
public class SkipInactiveEntityForExecute implements ConfigModule {

    @ConfigInfo(name = "skip-inactive-entity-for-execute-command", comments = {"Skip selecting inactive entities when using execute command.\nWill improve performance on servers with massive datapack functions.", "execute 命令执行时跳过不活跃实体.\n将会提升有大量数据包函数的服务器性能."})
    public static boolean skipInactiveEntityForExecute = false;
}
