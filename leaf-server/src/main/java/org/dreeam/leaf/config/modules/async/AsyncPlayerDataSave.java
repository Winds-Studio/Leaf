package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean playerdata = false;
    public static boolean advancements = false;
    public static boolean stats = false;
    public static boolean levelData = false;
    public static boolean userList = false;
    public static boolean profileCache = true;
    private static boolean asyncPlayerDataSavingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Save file asynchronously.""",
            """
                异步保存文件.""");

        if (asyncPlayerDataSavingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncPlayerDataSavingInitialized = true;

        advancements = get("advancements", advancements);
        playerdata = get("playerdata", playerdata);
        stats = get("stats", stats);
        levelData = get("level-data", levelData);
        userList = get("user-list", userList);
        profileCache = get("profile-cache", profileCache);
    }

    private boolean get(String s, boolean def) {
        return config.getBoolean(getBasePath() + '.' + s, def);
    }
}
