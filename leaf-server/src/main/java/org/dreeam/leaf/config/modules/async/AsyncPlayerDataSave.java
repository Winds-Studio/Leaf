package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Make PlayerData saving asynchronously.""",
            """
                异步保存玩家数据.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);

        if (enabled) {
            org.dreeam.leaf.async.AsyncPlayerDataSaving.init();
        }
    }
}
