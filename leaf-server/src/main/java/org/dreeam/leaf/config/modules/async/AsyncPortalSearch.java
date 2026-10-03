package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class AsyncPortalSearch extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".portal-search-prefetch";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Pre-fetches and loads target dimension chunks when an entity enters a nether portal.""",
            """
                实体进入下界传送门时异步预加载目标维度的区块, 减少传送卡顿.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
