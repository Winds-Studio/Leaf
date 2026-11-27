package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class AsyncPlayerDataSave extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-playerdata-save";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Make PlayerData saving asynchronously.""",
            """
                异步保存玩家数据.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);

        if (enabled) {
            org.dreeam.leaf.async.AsyncPlayerDataSaving.init();
        }
    }
}
