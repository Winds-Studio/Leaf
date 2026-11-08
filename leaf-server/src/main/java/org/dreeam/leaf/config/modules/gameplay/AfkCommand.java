package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "afk-command")
public class AfkCommand implements ConfigModule {

    @ConfigInfo(name = "enabled", comments = {
        """
            The AFK command based on Minecraft built-in idle-timeout mechanism
            Rest of AFK settings are in the Purpur config""",
        """
            基于原版 idle-timeout 系统的 AFK 指令
            剩余配置项在 Purpur 配置里"""
    })
    public static boolean enabled = false;
}
