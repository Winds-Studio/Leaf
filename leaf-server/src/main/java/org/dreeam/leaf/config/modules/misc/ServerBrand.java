package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class ServerBrand extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".rebrand";
    }

    public static String serverModName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName();
    public static String serverGUIName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName() + " Console";

    @Override
    public void onLoaded() {
        serverModName = globalConfig.getString(basePath() + ".server-mod-name", serverModName);
        serverGUIName = globalConfig.getString(basePath() + ".server-gui-name", serverGUIName);
    }
}
