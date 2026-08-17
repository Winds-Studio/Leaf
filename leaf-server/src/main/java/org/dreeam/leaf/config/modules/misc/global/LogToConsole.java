package org.dreeam.leaf.config.modules.misc.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "log-to-console")
public final class LogToConsole implements ConfigModule {

    @ConfigInfo(name = "invalid-statistics")
    public static boolean invalidStatistics = true;

    @ConfigInfo(name = "ignored-advancements")
    public static boolean ignoredAdvancements = true;

    @ConfigInfo(name = "set-block-in-far-chunk")
    public static boolean setBlockInFarChunk = true;

    @ConfigInfo(name = "unrecognized-recipes")
    public static boolean unrecognizedRecipes = false;

    @ConfigInfo(name = "legacy-material-initialization")
    public static boolean legacyMaterialInitialization = false;

    @ConfigInfo(name = "null-id-disconnections")
    public static boolean nullIdDisconnections = true;

    @ConfigInfo(name = "player-login-locations")
    public static boolean playerLoginLocations = true;

    @ConfigInfo(name = "invalid-legacy-text-component")
    public static boolean invalidLegacyTextComponent = true;

}
