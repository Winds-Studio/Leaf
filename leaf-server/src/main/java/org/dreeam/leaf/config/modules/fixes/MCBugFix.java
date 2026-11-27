package org.dreeam.leaf.config.modules.fixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class MCBugFix extends ConfigModule {

    public String basePath() {
        return ConfigCategory.FIXES.basePath() + ".vanilla-bug-fix";
    }

    public static final Logger LOGGER = LogManager.getLogger("Leaf Vanilla Bug Fix");

    public static boolean mc270656 = false;
    public static boolean mc301114 = false;
    public static int mc301114maxCombatEntries = 10240;
    public static boolean mc152094 = false;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(),
            "Fixes for vanilla Minecraft bugs.",
            "针对 Minecraft 原版的漏洞修复。"
        );
        mc270656 = globalConfig.getBoolean(basePath() + ".mc-270656", mc270656, globalConfig.pickStringRegionBased(
            """
                Whether to fix incorrect granting of 'Who needs rockets?' advancement.
                Mojira link: https://mojira.dev/MC-270656""",
            """
                是否修复“还要啥火箭啊？”进度触发的错误检查逻辑。
                漏洞跟踪器链接：https://mojira.dev/MC-270656"""
        ));
        mc301114 = globalConfig.getBoolean(basePath() + ".mc-301114", mc301114, globalConfig.pickStringRegionBased(
            """
                Whether to fix the memory leak in the combat tracker caused by the mob constantly being damaged.
                Mojira link: https://mojira.dev/MC-301114""",
            """
                是否修复战斗跟踪器（Combat Tracker）中，因生物受到持续性伤害导致的内存泄漏。
                漏洞跟踪器链接：https://mojira.dev/MC-301114"""
        ));
        mc301114maxCombatEntries = globalConfig.getInt(basePath() + ".mc-301114-max-entries", mc301114maxCombatEntries, globalConfig.pickStringRegionBased(
            "Max allowed entries in mob's combat tracker.",
            "生物战斗跟踪器中允许的最大条目。"
        ));

        mc301114maxCombatEntries = Math.max(1, mc301114maxCombatEntries);

        mc152094 = globalConfig.getBoolean(basePath() + ".mc-152094", mc152094, globalConfig.pickStringRegionBased(
            """
                Whether to fix the bug End City ship generation gets cut at chunk borders.
                Mojira link: https://mojira.dev/MC-152094""",
            """
                是否修复末地船在区块边缘生成时被截断的问题。
                漏洞跟踪器链接：https://mojira.dev/MC-152094"""
        ));
    }
}
