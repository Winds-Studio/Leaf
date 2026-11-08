package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "connection-message", comments = {
    """
        Connection message, using MiniMessage format, set to "default" to use vanilla join message.
        available placeholders:
        <player_name> - player name
        <player_displayname> - player display name""",
    """
        自定义加入 & 退出消息 (MiniMessage 格式), 设置为 'default' 将使用原版消息.
        可用的内置变量:
        <player_name> - 玩家名称
        <player_displayname> - 玩家显示名称"""
})
public class ConnectionMessage implements ConfigModule {

    @ConfigInfo(name = "join.enabled")
    public static boolean joinEnabled = true;
    @ConfigInfo(name = "join.message", comments = {
        "Join message of player",
        "玩家加入服务器时的消息"
    })
    public static String joinMessage = "default";

    @ConfigInfo(name = "quit.enabled")
    public static boolean quitEnabled = true;
    @ConfigInfo(name = "quit.message", comments = {
        "Quit message of player",
        "玩家退出服务器时的消息"
    })
    public static String quitMessage = "default";

    @Override
    public void onLoaded() {
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
