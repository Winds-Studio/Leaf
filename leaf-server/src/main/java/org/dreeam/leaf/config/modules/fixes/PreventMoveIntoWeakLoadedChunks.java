package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class PreventMoveIntoWeakLoadedChunks extends ConfigModule {

    public String basePath() {
        return ConfigCategory.FIXES.basePath() + ".prevent-moving-into-weak-loaded-chunks";
    }

    public static boolean enabled = false;
    public static boolean projectiles = false;

    public static boolean isProjectileEnabled() {
        return enabled && projectiles;
    }

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(),
            "Prevents entities from moving into weak loaded chunks.",
            "阻止实体进入弱加载区块。"
        );

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled, globalConfig.pickStringRegionBased(
            "Set to true to enable features below.",
            "设置为 true 以启用以下功能。"
        ));

        projectiles = globalConfig.getBoolean(basePath() + ".projectiles", projectiles, globalConfig.pickStringRegionBased(
            "Prevents projectiles from moving into weak loaded chunks.",
            "阻止弹射物进入弱加载区块。"
        ));
    }
}
