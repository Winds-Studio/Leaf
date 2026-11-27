package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class ConnectionMessage extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".connection-message";
    }

    public static boolean joinEnabled = true;
    public static String joinMessage = "default";
    public static boolean quitEnabled = true;
    public static String quitMessage = "default";

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Connection message, using MiniMessage format, set to "default" to use vanilla join message.
                available placeholders:
                <player_name> - player name
                <player_displayname> - player display name""",
            """
                自定义加入 & 退出消息 (MiniMessage 格式), 设置为 'default' 将使用原版消息.
                可用的内置变量:
                <player_name> - 玩家名称
                <player_displayname> - 玩家显示名称""");

        joinEnabled = globalConfig.getBoolean(basePath() + ".join.enabled", joinEnabled);
        joinMessage = globalConfig.getString(basePath() + ".join.message", joinMessage, globalConfig.pickStringRegionBased(
            "Join message of player",
            "玩家加入服务器时的消息"
        ));

        quitEnabled = globalConfig.getBoolean(basePath() + ".quit.enabled", quitEnabled);
        quitMessage = globalConfig.getString(basePath() + ".quit.message", quitMessage, globalConfig.pickStringRegionBased(
            "Quit message of player",
            "玩家退出服务器时的消息"));

        // Legacy compatibility
        // TODO: config migration
        joinMessage = joinMessage
            .replace("%player_name%", "<player_name>")
            .replace("%player_displayname%", "<player_displayname>");
        quitMessage = quitMessage
            .replace("%player_name%", "<player_name>")
            .replace("%player_displayname%", "<player_displayname>");
    }
}
