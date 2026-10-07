package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class ProjectileMarginExpansion extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath();
    }

    public static boolean disableProjectileMarginExpansion = false;

    @Override
    public void onLoaded() {
        disableProjectileMarginExpansion = globalConfig.getBoolean(basePath() + ".disable-projectile-margin-expansion", disableProjectileMarginExpansion,
            "Give projectiles the full hit margin right away, so close range shots don't miss (pre 1.21.6 behavior).");
    }
}
