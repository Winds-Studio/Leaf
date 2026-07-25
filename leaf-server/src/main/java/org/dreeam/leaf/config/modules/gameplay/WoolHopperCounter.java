package org.dreeam.leaf.config.modules.gameplay;


import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class WoolHopperCounter extends ConfigModule {

    public String getBasePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".wool-hopper-counter";
    }

    public static boolean enabled = false;
    public static boolean unlimited = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(getBasePath() + ".enabled", enabled);
        unlimited = globalConfig.getBoolean(getBasePath() + "unlimited-speed", unlimited);
    }
}
