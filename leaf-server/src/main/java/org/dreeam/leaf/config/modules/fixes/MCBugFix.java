package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class MCBugFix extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.FIXES.getBaseKeyName() + ".vanilla-bug-fix";
    }

    public static boolean mc270656 = false;
    public static boolean mc301114 = false;
    public static int mc301114maxCombatEntries = 10240;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "Fixes for vanilla Minecraft bugs.",
            "针对 Minecraft 原版的漏洞修复。"
        );
        mc270656 = config.getBoolean(getBasePath() + ".mc-270656", mc270656, config.pickStringRegionBased(
            """
                Whether to fix incorrect granting of 'Who needs rockets?' advancement.
                Mojira link: https://mojira.dev/MC-270656""",
            """
                是否修复“还要啥火箭啊？”进度触发的错误检查逻辑。
                漏洞跟踪器链接：https://mojira.dev/MC-270656"""
        ));
        mc301114 = config.getBoolean(getBasePath() + ".mc-301114", mc301114, config.pickStringRegionBased(
            """
                Whether to fix the memory leak in the combat tracker caused by the mob constantly being damaged.
                Mojira link: https://mojira.dev/MC-301114""",
            """
                是否修复战斗跟踪器（Combat Tracker）中，因生物受到持续性伤害导致的内存泄漏。
                漏洞跟踪器链接：https://mojira.dev/MC-301114"""
        ));
        mc301114maxCombatEntries = config.getInt(getBasePath() + ".mc-301114-max-entries", mc301114maxCombatEntries, config.pickStringRegionBased(
            "Max allowed entries in mob's combat tracker.",
            "生物战斗跟踪器中允许的最大条目。"
        ));
    }
}
