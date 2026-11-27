package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class AfkCommand extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".afk-command";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled, globalConfig.pickStringRegionBased("""
                The AFK command based on Minecraft built-in idle-timeout mechanism
                Rest of AFK settings are in the Purpur config""",
            """
                基于原版 idle-timeout 系统的 AFK 指令
                剩余配置项在 Purpur 配置里"""));
    }
}
