package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class PluginBackup extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".plugin-backup";
    }

    public static boolean enabled = true;
    public static String backupFolderName = "pluginsBackup";

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled, config.pickStringRegionBased(
                "Enable plugin backup before updating from the update folder.",
                "在从 update 文件夹更新插件之前启用插件备份."
            ));
        backupFolderName = config.getString(getBasePath() + ".backup-folder-name", backupFolderName, config.pickStringRegionBased(
                "The name of the backup folder where old plugins will be stored.",
                "用于存储旧插件的备份文件夹名称."
            ));
    }
}
