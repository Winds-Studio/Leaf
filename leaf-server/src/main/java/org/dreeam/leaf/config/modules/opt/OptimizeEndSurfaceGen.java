package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "optimize-end-surface-gen")
public class OptimizeEndSurfaceGen implements ConfigModule {

    @ConfigInfo(name = "enabled", comments = {"Skip trivial End surface rule build process.\nMay be incompatible with some datapacks that modify End world generation.", "跳过不必要的末地 surface rule 构建。\n可能不兼容一些修改末地世界生成的数据包。"})
    public static boolean enabled = false;
}
