package org.dreeam.leaf.config;

import io.papermc.paper.SparksFly;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.misc.SentryDSN;
import org.dreeam.leaf.config.modules.opt.FastBiomeManagerSeedObfuscation;
import org.jspecify.annotations.NullMarked;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
 *  Yoinked from: https://github.com/xGinko/AnarchyExploitFixes/ & https://github.com/LuminolMC/Luminol
 *  @author: @xGinko & @MrHua269
 */
@NullMarked
public class LeafConfig {

    public static final Logger LOGGER = LogManager.getLogger(LeafConfig.class.getSimpleName());

    public static final String CURRENT_CONFIG_VERSION = "3.0";
    // It will be in uppercase by default, just make sure
    private static final String REGION_COUNTRY_CODE = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT);
    private static final boolean IS_CHINESE_LOCALE = REGION_COUNTRY_CODE.equals("CN");

    protected static final File CONFIG_DIRECTORY = new File("config");
    protected static final String CONFIG_MODULE_PACKAGE = "org.dreeam.leaf.config.modules";
    protected static final String GLOBAL_CONFIG_FILE = "leaf-global.yml";
    protected static final String DEFAULT_WORLD_CONFIG_FILE = "leaf-world-defaults.yml";
    protected static final String WORLD_CONFIG_FILE = "leaf-world.yml";

    private static final String SPARK_EXTRA_CONFIG_PROPERTY =  "spark.serverconfigs.extra";
    private static final String SPARK_HIDDEN_PATHS_PROPERTY =  "spark.serverconfigs.hiddenpaths";

    private static LeafGlobalConfig globalConfig;
    private static LeafWorldConfig worldDefaultsConfig;

    private static ConfigVersion previousConfigVersion = ConfigVersion.initial();

    /* Load & Reload */

    // Reload config (async)
    public static CompletableFuture<Void> reloadAsync(CommandSender sender) {
        return CompletableFuture.runAsync(() -> {
            try {
                long begin = System.nanoTime();

                ConfigModule.clearModules();
                loadConfig(false);
                ConfigModule.loadAfterBootstrap();

                final String success = String.format("Successfully reloaded config in %sms.", (System.nanoTime() - begin) / 1_000_000);
                Command.broadcastCommandMessage(sender, Component.text(success, NamedTextColor.GREEN));
            } catch (Exception e) {
                Command.broadcastCommandMessage(sender, Component.text("Failed to reload config. See error in console!", NamedTextColor.RED));
                LOGGER.error("Failed to reload config!", e);
            }
        }, Util.ioPool());
    }

    // Init config
    public static void loadConfig() {
        try {
            long begin = System.nanoTime();
            LOGGER.info("Loading config...");

            purgeOutdated();
            loadConfig(true);

            LOGGER.info("Successfully loaded config in {}ms.", (System.nanoTime() - begin) / 1_000_000);
        } catch (Exception e) {
            LOGGER.error("Failed to load config modules!", e);
        }
    }

    /* Load Global Config */

    private static void loadConfig(boolean init) throws Exception {
        // Create config folder
        createDirectory(CONFIG_DIRECTORY);

        globalConfig = new LeafGlobalConfig(init);

        // Load config modules
        ConfigModule.initModules();

        File worldDefaultsFile = new File(CONFIG_DIRECTORY, DEFAULT_WORLD_CONFIG_FILE);
        if (!worldDefaultsFile.exists()) {
            globalConfig.saveConfig();
            Files.copy(new File(CONFIG_DIRECTORY, GLOBAL_CONFIG_FILE).toPath(), worldDefaultsFile.toPath());
        }
        worldDefaultsConfig = LeafWorldConfig.loadDefaults(worldDefaultsFile);
        worldDefaultsConfig.saveConfig();
    }

    public static LeafGlobalConfig globalConfig() {
        return globalConfig;
    }

    public static LeafWorldConfig worldDefaultsConfig() {
        return worldDefaultsConfig;
    }

    /**
     * Loads an explicit world override without creating a file when the world uses the defaults.
     */
    public static LeafWorldConfig createWorldConfig(Path worldDirectory) {
        File worldConfigFile = worldDirectory.resolve(WORLD_CONFIG_FILE).toFile();
        if (!LeafWorldConfig.exists(worldConfigFile)) {
            return worldDefaultsConfig;
        }
        try {
            return new LeafWorldConfig(worldConfigFile, worldDefaultsConfig);
        } catch (Exception exception) {
            throw new RuntimeException("Could not load Leaf world config for " + worldDirectory, exception);
        }
    }

    static boolean isChineseLocale() {
        return IS_CHINESE_LOCALE;
    }

    /* Create config folder */

    protected static void createDirectory(File dir) throws IOException {
        try {
            Files.createDirectories(dir.toPath());
        } catch (FileAlreadyExistsException e) { // Thrown if dir exists but is not a directory
            if (dir.delete()) createDirectory(dir);
        }
    }

    /* Scan classes under package */

    public static Set<Class<?>> getClasses(String pack) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String packageDirName = pack.replace('.', '/');
        Enumeration<URL> dirs;

        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                    findClassesInPackageByFile(pack, filePath, classes);
                } else if ("jar".equals(protocol)) {
                    JarFile jar;
                    try {
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        Enumeration<JarEntry> entries = jar.entries();
                        findClassesInPackageByJar(pack, entries, packageDirName, classes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return classes;
    }

    private static void findClassesInPackageByFile(String packageName, String packagePath, Set<Class<?>> classes) {
        File dir = new File(packagePath);

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] dirfiles = dir.listFiles((file) -> file.isDirectory() || file.getName().endsWith(".class"));
        if (dirfiles != null) {
            for (File file : dirfiles) {
                if (file.isDirectory()) {
                    findClassesInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
                } else {
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static void findClassesInPackageByJar(String packageName, Enumeration<JarEntry> entries, String packageDirName, Set<Class<?>> classes) {
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }

            if (name.startsWith(packageDirName)) {
                int idx = name.lastIndexOf('/');

                if (idx != -1) {
                    packageName = name.substring(0, idx).replace('/', '.');
                }

                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Records the version that was stored in the global configuration before it is updated to
     * {@link #CURRENT_CONFIG_VERSION}. A missing version is treated as the initial configuration version
     * so migrations can also handle configurations created before versioning was introduced.
     *
     * @param previousVersion the value of {@code config-version}, or {@code null} when absent
     */
    public static void loadPreviousConfigVersion(String previousVersion) {
        if (previousVersion == null) {
            previousConfigVersion = ConfigVersion.initial();
            return;
        }

        try {
            previousConfigVersion = ConfigVersion.parse(previousVersion);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid Leaf config version '{}'; treating it as an unversioned configuration.", previousVersion);
            previousConfigVersion = ConfigVersion.initial();
        }
    }

    /**
     * Returns whether the configuration being loaded predates {@code version}.
     *
     * <p>Use this when migrating a renamed option path, before the current config version is
     * persisted. For example, {@code isConfigVersionBefore("3.1")} is {@code true} for a
     * configuration last written by Leaf 3.0.</p>
     *
     * @param version a numeric dot-separated config version, such as {@code 3.1}
     * @return {@code true} when the loaded configuration is older than {@code version}
     * @throws IllegalArgumentException if {@code version} is not a numeric dot-separated version
     */
    public static boolean isConfigVersionBefore(String version) {
        return previousConfigVersion.compareTo(ConfigVersion.parse(version)) < 0;
    }

    /**
     * Returns whether the configuration being loaded is at least {@code version}.
     *
     * @param version a numeric dot-separated config version, such as {@code 3.1}
     * @return {@code true} when the loaded configuration is not older than {@code version}
     * @throws IllegalArgumentException if {@code version} is not a numeric dot-separated version
     */
    public static boolean isConfigVersionAtLeast(String version) {
        return !isConfigVersionBefore(version);
    }

    private record ConfigVersion(List<Integer> components) implements Comparable<ConfigVersion> {

        private static ConfigVersion initial() {
            return new ConfigVersion(List.of(0));
        }

        private static ConfigVersion parse(String version) {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("Config version must not be blank");
            }

            String[] parts = version.split("\\.", -1);
            List<Integer> components = new ArrayList<>(parts.length);
            for (String part : parts) {
                if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                    throw new IllegalArgumentException("Invalid config version: " + version);
                }
                try {
                    components.add(Integer.parseInt(part));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid config version: " + version, exception);
                }
            }
            return new ConfigVersion(List.copyOf(components));
        }

        @Override
        public int compareTo(ConfigVersion other) {
            int componentCount = Math.max(this.components.size(), other.components.size());
            for (int index = 0; index < componentCount; index++) {
                int thisComponent = index < this.components.size() ? this.components.get(index) : 0;
                int otherComponent = index < other.components.size() ? other.components.get(index) : 0;
                int comparison = Integer.compare(thisComponent, otherComponent);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        }
    }

    /* Register Spark profiler extra server configurations */

    private static List<String> buildSparkExtraConfigs() {
        List<String> extraConfigs = new ArrayList<>(Arrays.asList(
            "config/leaf-global.yml",
            "config/leaf-world-defaults.yml",
            "config/gale-global.yml",
            "config/gale-world-defaults.yml"
        ));

        String existing = System.getProperty(SPARK_EXTRA_CONFIG_PROPERTY);
        if (existing != null) {
            extraConfigs.addAll(Arrays.asList(existing.split(",")));
        }

        // Use same way in spark's BukkitServerConfigProvider#getNestedFiles to get all world configs
        // It may spam in the spark profiler, but it's ok, since spark uses YamlConfigParser.INSTANCE
        // instead of using SplitYamlConfigParser.INSTANCE for the extra config
        // However it's better to choose bundled spark for better view.
        for (World world : Bukkit.getWorlds()) {
            Path galeWorldFolder = world.getWorldFolder().toPath().resolve("gale-world.yml");
            extraConfigs.add(galeWorldFolder.toString().replace("\\", "/").replace("./", "")); // Gale world config
            Path leafWorldFile = world.getWorldFolder().toPath().resolve(WORLD_CONFIG_FILE);
            if (Files.isRegularFile(leafWorldFile)) {
                extraConfigs.add(leafWorldFile.toString().replace("\\", "/").replace("./", ""));
            }
        }

        return extraConfigs;
    }

    private static List<String> buildSparkHiddenPaths() {
        String existing = System.getProperty(SPARK_HIDDEN_PATHS_PROPERTY);

        List<String> extraHidden = existing != null ? new ArrayList<>(Arrays.asList(existing.split(","))) : new ArrayList<>();
        extraHidden.add(SentryDSN.sentryDsnConfigPath); // Hide Sentry DSN key
        extraHidden.add(FastBiomeManagerSeedObfuscation.seedObfKeyPath); // Hide FastBiomeManagerSeedObfuscation key

        return extraHidden;
    }

    // Sync with LeafServerConfigProvider
    public static void regSparkExtraConfig() {
        // Spark plugin is used
        if (SparksFly.isPluginPreferred() && Bukkit.getServer().getPluginManager().getPlugin("spark") != null) {
            String extraConfigs = String.join(",", buildSparkExtraConfigs());
            System.setProperty(SPARK_EXTRA_CONFIG_PROPERTY, extraConfigs);

            String hiddenPaths = String.join(",", buildSparkHiddenPaths());
            System.setProperty(SPARK_HIDDEN_PATHS_PROPERTY, hiddenPaths);
        }
    }

    /* Purge and backup old Leaf config & Pufferfish config */

    private static void purgeOutdated() {
        boolean foundLegacy = false;
        String pufferfishConfig = "pufferfish.yml";
        String leafConfigV1 = "leaf.yml";
        String leafConfigV2 = "leaf_config";

        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddhhmmss");
        String backupDir = "config/backup" + dateFormat.format(date) + "/";

        File pufferfishConfigFile = new File(pufferfishConfig);
        File leafConfigV1File = new File(leafConfigV1);
        File leafConfigV2File = new File(leafConfigV2);
        File backupDirFile = new File(backupDir);

        try {
            if (pufferfishConfigFile.exists() && pufferfishConfigFile.isFile()) {
                createDirectory(backupDirFile);
                Files.move(pufferfishConfigFile.toPath(), Path.of(backupDir + pufferfishConfig), StandardCopyOption.REPLACE_EXISTING);
                foundLegacy = true;
            }
            if (leafConfigV1File.exists() && leafConfigV1File.isFile()) {
                createDirectory(backupDirFile);
                Files.move(leafConfigV1File.toPath(), Path.of(backupDir + leafConfigV1), StandardCopyOption.REPLACE_EXISTING);
                foundLegacy = true;
            }
            if (leafConfigV2File.exists() && leafConfigV2File.isDirectory()) {
                createDirectory(backupDirFile);
                Files.move(leafConfigV2File.toPath(), Path.of(backupDir + leafConfigV2), StandardCopyOption.REPLACE_EXISTING);
                foundLegacy = true;
            }

            if (foundLegacy) {
                LOGGER.warn("Found legacy Leaf config files, move to backup directory: {}", backupDir);
                LOGGER.warn("New Leaf config located at config/ folder, You need to transfer config to the new one manually and restart the server!");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to purge old configs.", e);
        }
    }
}
