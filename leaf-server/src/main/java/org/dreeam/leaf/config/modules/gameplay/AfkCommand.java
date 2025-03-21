package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AfkCommand extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".afk-command";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled, config.pickStringRegionBased("""
                The AFK command based on Minecraft built-in idle-timeout mechanism
                Rest of AFK settings are in the Purpur config""",
            """
                基于原版 idle-timeout 系统的 AFK 指令
                剩余配置项在 Purpur 配置里"""));
    }
}
