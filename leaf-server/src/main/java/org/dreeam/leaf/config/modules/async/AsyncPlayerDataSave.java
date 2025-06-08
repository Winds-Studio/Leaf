package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.storage.AsyncPlayerDataSaving;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    @Experimental
    public static boolean enabled = false;
    private static boolean asyncPlayerDataSaveInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Make PlayerData saving asynchronously.""",
            """
                异步保存玩家数据.""");

        if (asyncPlayerDataSaveInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncPlayerDataSaveInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        if (enabled) AsyncPlayerDataSaving.init();
    }
}
