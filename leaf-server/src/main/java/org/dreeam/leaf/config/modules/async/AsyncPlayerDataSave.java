package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPlayerDataSave extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-playerdata-save";
    }

    public static boolean enabled = false;
    public static boolean playerdata = false;
    public static boolean advancements = false;
    public static boolean stats = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Asynchronously save player.""",
            """
                异步保存玩家数据.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        boolean advancements = config.getBoolean(getBasePath() + ".advancements", false);
        boolean playerdata = config.getBoolean(getBasePath() + ".playerdata", false);
        boolean stats = config.getBoolean(getBasePath() + ".stats", false);
        AsyncPlayerDataSave.advancements = enabled && advancements;
        AsyncPlayerDataSave.playerdata = enabled && playerdata;
        AsyncPlayerDataSave.stats = enabled && stats;
    }
}
