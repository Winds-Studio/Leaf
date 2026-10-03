package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class AsyncLocate extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-locate";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Executes /locate structure and biome searches asynchronously on a worker thread.""",
            """
                在后台异步线程执行 /locate 结构与生物群系搜索, 防止卡死主线程.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
