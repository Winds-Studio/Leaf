package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class MCBugFix extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".vanilla-bug-fix";
    }

    public static boolean mc270656 = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "Fixes for vanilla Minecraft bugs.",
            "针对 Minecraft 原版的漏洞修复."
        );
        mc270656 = config.getBoolean(getBasePath() + ".mc-270656", mc270656, config.pickStringRegionBased(
            """
                Who needs rockets? advancement is being granted incorrectly.
                Mojira link: https://mojira.dev/MC-270656""",
            """
                还要啥火箭啊? 进度触发器检查逻辑漏洞.
                漏洞跟踪器链接: https://mojira.dev/MC-270656"""
        ));
    }
}
