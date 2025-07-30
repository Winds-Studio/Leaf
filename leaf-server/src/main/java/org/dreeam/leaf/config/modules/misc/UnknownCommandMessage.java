package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class UnknownCommandMessage extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".message";
    }

    public static String unknownCommandMessage = "default";

    @Override
    public void onLoaded() {
        unknownCommandMessage = config.getString(getBasePath() + ".unknown-command", unknownCommandMessage, config.pickStringRegionBased("""
                Unknown command message, using MiniMessage format, set to "default" to use vanilla message,
                placeholder:
                <message>, show message of the command exception.
                <detail>, shows detail of the command exception.""",
            """
                发送未知命令时的消息, 使用 MiniMessage 格式, 设置为 "default" 使用原版消息.
                变量:
                <message>, 显示命令错误所附提示消息.
                <detail>, 显示命令错误详细信息."""));
    }
}
