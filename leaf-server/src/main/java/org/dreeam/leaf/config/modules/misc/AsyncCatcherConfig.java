package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncCatcherConfig extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".async-catcher";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                !!! WARNING !!!
                Disabling this is NOT recommended and can lead to severe server instability.
                Only disable this if you are an advanced user or debugging a specific issue
                and understand the risks involved.""",
            """
                !!! 警告 !!!
                不建议禁用此功能,因为它可能导致严重的服务器不稳定.
                只有当您是正在调试特定问题并了解相关风险的高级用户时,才应禁用此功能.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
    }
}
