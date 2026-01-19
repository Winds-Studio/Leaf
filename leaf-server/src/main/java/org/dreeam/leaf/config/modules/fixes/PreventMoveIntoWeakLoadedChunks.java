package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class PreventMoveIntoWeakLoadedChunks extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".prevent-moving-into-weak-loaded-chunks";
    }

    public static boolean enabled = false;
    public static boolean throwableProjectiles = false;

    public static boolean isThrowableProjectilesEnabled() {
        return enabled && throwableProjectiles;
    }

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "Prevents entities from moving into weak loaded chunks.",
            "阻止实体进入弱加载区块."
        );

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled, config().pickStringRegionBased(
            "Set to true to enable features below.",
            "设置为 true 以启用以下功能."
        ));

        throwableProjectiles = config.getBoolean(getBasePath() + ".throwable-projectiles", throwableProjectiles, config().pickStringRegionBased(
            "Prevents throwable projectiles from moving into weak loaded chunks.",
            "阻止投掷物进入弱加载区块."
        ));
    }
}
