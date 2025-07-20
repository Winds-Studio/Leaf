package org.dreeam.leaf.config;

import io.papermc.paper.configuration.GlobalConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.misc.SentryDSN;
import org.dreeam.leaf.config.modules.opt.FastBiomeManagerSeedObfuscation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
 *  Yoinked from: https://github.com/xGinko/AnarchyExploitFixes/ & https://github.com/LuminolMC/Luminol
 *  @author: @xGinko & @MrHua269
 *
 *  Rewritten by @Taiyou
 */
public class LeafConfig {

    public static final Logger LOGGER = LogManager.getLogger(LeafConfig.class.getSimpleName());
    protected static final File I_CONFIG_FOLDER = new File("config");
    protected static final File I_LEAF_CONFIG_FOLDER = new File(I_CONFIG_FOLDER, "leaf");
    protected static final String I_CONFIG_PKG = "org.dreeam.leaf.config.modules";
    protected static final String I_GLOBAL_CONFIG_FILE = "leaf-global.yml";

    private static Map<EnumConfigCategory, LeafCategoryConfig> categoryConfigs = new HashMap<>();

    /* Load & Reload */

    // Reload config (async)
    public static @NotNull CompletableFuture<Void> reloadAsync(CommandSender sender) {
        return CompletableFuture.runAsync(() -> {
            try {
                long begin = System.nanoTime();

                ConfigModules.clearModules();
                loadConfig(false);
                ConfigModules.loadAfterBootstrap();

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

            // Auto-migrate if old config exists
            autoMigrateIfNeeded();

            loadConfig(true);

            LOGGER.info("Successfully loaded config in {}ms.", (System.nanoTime() - begin) / 1_000_000);
        } catch (Exception e) {
            LOGGER.error("Failed to load config modules!", e);
        }
    }

    /* Load Global Config */

    private static void loadConfig(boolean init) throws Exception {
        // Create leaf config folder
        createDirectory(I_LEAF_CONFIG_FOLDER);

        // Load all category configs
        categoryConfigs.clear();
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            categoryConfigs.put(category, new LeafCategoryConfig(category, init));
        }

        // Load config modules
        ConfigModules.initModules();
    }

    public static LeafCategoryConfig config(EnumConfigCategory category) {
        return categoryConfigs.get(category);
    }

    // For backward compatibility - returns misc config
    @Deprecated
    public static LeafCategoryConfig config() {
        return categoryConfigs.get(EnumConfigCategory.MISC);
    }

    /* Auto-migration logic */

    private static void autoMigrateIfNeeded() {
        File oldConfigFile = new File(I_CONFIG_FOLDER, I_GLOBAL_CONFIG_FILE);
        if (!oldConfigFile.exists()) {
            return; // No old config to migrate
        }

        // Check if any category files are missing
        boolean needsMigration = false;
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            File categoryFile = new File(I_LEAF_CONFIG_FOLDER, category.getFileName());
            if (!categoryFile.exists()) {
                needsMigration = true;
                break;
            }
        }

        if (needsMigration) {
            LOGGER.info("Detected old config file. Automatically migrating to multi-file system...");
            try {
                ConfigMigrator migrator = new ConfigMigrator();
                migrator.migrate();
                LOGGER.info("Migration completed successfully!");
            } catch (Exception e) {
                LOGGER.error("Auto-migration failed!", e);
                throw new RuntimeException("Failed to migrate config", e);
            }
        }
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

    public static @NotNull Set<Class<?>> getClasses(String pack) {
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

    /* Register Spark profiler extra server configurations */

    public static void regSparkExtraConfig() {
        if (GlobalConfiguration.get().spark.enabled || Bukkit.getServer().getPluginManager().getPlugin("spark") != null) {
            List<String> extraConfigs = new ArrayList<>();

            // Add all category config files from config/leaf folder (individual files)
            for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
                extraConfigs.add("config/leaf/" + category.getFileName());
            }

            // Add other configs
            extraConfigs.addAll(Arrays.asList(
                "config/gale-global.yml",
                "config/gale-world-defaults.yml"
            ));

            @Nullable String existing = System.getProperty("spark.serverconfigs.extra");
            if (existing != null) {
                extraConfigs.addAll(Arrays.asList(existing.split(",")));
            }

            // Use same way in spark's BukkitServerConfigProvider#getNestedFiles to get all world configs
            // It may spam in the spark profiler, but it's ok, since spark uses YamlConfigParser.INSTANCE to
            // get configs defined in extra config flag instead of using SplitYamlConfigParser.INSTANCE
            for (World world : Bukkit.getWorlds()) {
                Path galeWorldFolder = world.getWorldFolder().toPath().resolve("gale-world.yml");
                extraConfigs.add(galeWorldFolder.toString().replace("\\", "/").replace("./", "")); // Gale world config
            }

            String extraConfigsStr = String.join(",", extraConfigs);
            String hiddenPaths = String.join(",", buildSparkHiddenPaths());

            System.setProperty("spark.serverconfigs.extra", extraConfigsStr);
            System.setProperty("spark.serverconfigs.hiddenpaths", hiddenPaths);
        }
    }

    private static List<String> buildSparkHiddenPaths() {
        @Nullable String existing = System.getProperty("spark.serverconfigs.hiddenpaths");

        List<String> extraHidden = existing != null ? new ArrayList<>(Arrays.asList(existing.split(","))) : new ArrayList<>();
        extraHidden.add(SentryDSN.sentryDsnConfigPath); // Hide Sentry DSN key
        extraHidden.add(FastBiomeManagerSeedObfuscation.seedObfKeyPath); // Hide FastBiomeManagerSeedObfuscation key

        return extraHidden;
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
                LOGGER.warn("New Leaf config located at config/leaf/ folder, You need to transfer config to the new one manually and restart the server!");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to purge old configs.", e);
        }
    }
}
