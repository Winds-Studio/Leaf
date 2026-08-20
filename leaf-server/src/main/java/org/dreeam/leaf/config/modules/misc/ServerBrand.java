package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "rebrand")
public class ServerBrand implements ConfigModule {

    @ConfigInfo(name = "server-mod-name")
    public static String serverModName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName();

    @ConfigInfo(name = "server-gui-name")
    public static String serverGUIName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName() + " Console";
}
