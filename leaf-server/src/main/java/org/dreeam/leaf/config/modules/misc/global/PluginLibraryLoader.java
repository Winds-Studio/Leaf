package org.dreeam.leaf.config.modules.misc.global;

import org.bukkit.plugin.java.JavaPluginLoader;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "plugin-library-loader")
public final class PluginLibraryLoader implements ConfigModule {

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
