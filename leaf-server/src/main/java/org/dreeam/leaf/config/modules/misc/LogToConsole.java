package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.bukkit.plugin.java.JavaPluginLoader;

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

    @ConfigClassInfo(category = ConfigCategory.MISC, name = "log-to-console.chat-logging")
    public static final class Chat implements ConfigModule {

        @ConfigInfo(name = "empty-message-warning")
        public static boolean emptyMessageWarning = false;

        @ConfigInfo(name = "expired-message-warning")
        public static boolean expiredMessageWarning = false;

        @ConfigInfo(name = "not-secure-marker")
        public static boolean notSecureMarker = true;
    }

    @ConfigClassInfo(category = ConfigCategory.MISC, name = "log-to-console.plugin-library-loader")
    public static final class PluginLibraryLoader implements ConfigModule {

        @ConfigInfo(name = "downloads")
        public static boolean downloads = true;

        @ConfigInfo(name = "start-load-libraries-for-plugin")
        public static boolean startLoadLibrariesForPlugin = true;

        @ConfigInfo(name = "library-loaded")
        public static boolean libraryLoaded = true;

        @Override
        public void onLoaded() {
            JavaPluginLoader.logDownloads = downloads;
            JavaPluginLoader.logStartLoadLibrariesForPlugin = startLoadLibrariesForPlugin;
            JavaPluginLoader.logLibraryLoaded = libraryLoaded;
        }
    }
}
