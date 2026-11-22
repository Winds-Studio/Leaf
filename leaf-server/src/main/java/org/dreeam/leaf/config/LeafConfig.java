package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.papermc.paper.SparksFly;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.annotations.Experimental;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;
import org.dreeam.leaf.config.migration.LeafConfigMigration;
import org.dreeam.leaf.config.migration.ConfigBackupSession;
import org.dreeam.leaf.config.migration.gale.GaleConfigMigration;
import org.dreeam.leaf.config.modules.misc.SentryDSN;
import org.dreeam.leaf.config.util.ConfigFileIO;
import org.dreeam.leaf.config.util.ConfigVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
 *  Yoinked from: https://github.com/xGinko/AnarchyExploitFixes/ & https://github.com/LuminolMC/Luminol
 *  @author: @xGinko & @MrHua269
 */
@NullMarked
public class LeafConfig {

    public static final Logger LOGGER = LogManager.getLogger(LeafConfig.class.getSimpleName());

    public static final String CURRENT_CONFIG_VERSION = "3.1";
    public static final String VERSION_PATH = "config-version";
    // It will be in uppercase by default, just make sure
    private static final String REGION_COUNTRY_CODE = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT);
    private static final boolean IS_CHINESE_LOCALE = REGION_COUNTRY_CODE.equals("CN");

    public static final File CONFIG_DIRECTORY = new File("config");
    protected static final String CONFIG_MODULE_PACKAGE = "org.dreeam.leaf.config.modules";
    protected static final String GLOBAL_CONFIG_FILE = "leaf-global.yml";
    protected static final String DEFAULT_WORLD_CONFIG_FILE = "leaf-world-defaults.yml";
    protected static final String WORLD_CONFIG_FILE = "leaf-world.yml";

    private static final String SPARK_EXTRA_CONFIG_PROPERTY =  "spark.serverconfigs.extra";
    private static final String SPARK_HIDDEN_PATHS_PROPERTY =  "spark.serverconfigs.hiddenpaths";

    private static LeafGlobalConfig globalConfig;
    private static LeafWorldConfig worldDefaultsConfig;

    private static final List<ConfigModule> GLOBAL_MODULES = new ArrayList<>();
    private static final List<Field> WORLD_MODULES = new ArrayList<>();


    /* Load & Reload */

    /**
     * Reloads the configuration on the calling thread, which has to be the server thread, like Paper does.
     * Nothing is queued or locked, and a reload is not undone when a step after the commit fails.
     */
    public static void reload(CommandSender sender) {
        long begin = System.nanoTime();
        MinecraftServer server = MinecraftServer.getServer();
        List<LoadedLevel> levels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.add(new LoadedLevel(server.storageSource.getDimensionPath(level.dimension()), level.leafConfig()));
        }

        try {
            ConfigFileIO.SaveReport saveReport = reloadConfigs(levels);

            final String success = String.format("Successfully reloaded config in %sms.", (System.nanoTime() - begin) / 1_000_000);
            Command.broadcastCommandMessage(sender, Component.text(success, NamedTextColor.GREEN));
            if (saveReport.hasAccessDenied()) {
                Command.broadcastCommandMessage(sender, Component.text(
                    "Leaf config reload is active in memory, but could not persist: " + saveReport.describe(),
                    NamedTextColor.YELLOW
                ));
            }
        } catch (ReloadException exception) {
            switch (exception.stage()) {
                case CANDIDATE -> {
                    Command.broadcastCommandMessage(sender, Component.text("Failed to reload config. See error in console!", NamedTextColor.RED));
                    LOGGER.error("Failed to reload config!", exception.getCause());
                }
                case CALLBACK -> {
                    Command.broadcastCommandMessage(sender, Component.text(
                        "Leaf config was reloaded, but post-reload processing failed, so it was not saved. See error in console!",
                        NamedTextColor.RED
                    ));
                    LOGGER.error("Leaf config reload post-processing failed after the configuration was applied; it was not saved.", exception.getCause());
                }
                case SAVE -> {
                    Command.broadcastCommandMessage(sender, Component.text(
                        "Leaf config reload is active in memory, but persistence failed: " + exception.report().describe(),
                        NamedTextColor.RED
                    ));
                    LOGGER.error("Leaf config reload persistence failed: {}", exception.report().describe(), exception.getCause());
                }
            }
        }
    }

    /** A loaded level: where its world configuration is stored, and the configuration it runs with. */
    record LoadedLevel(Path worldDirectory, LeafWorldConfig config) {
    }

    /** Where a reload stopped. Before {@link #CALLBACK} nothing was changed. */
    enum ReloadStage {
        CANDIDATE,
        CALLBACK,
        SAVE
    }

    static final class ReloadException extends Exception {

        private final ReloadStage stage;
        private final ConfigFileIO.@Nullable SaveReport report;

        private ReloadException(ReloadStage stage, Throwable cause, ConfigFileIO.@Nullable SaveReport report) {
            super(cause);
            this.stage = stage;
            this.report = report;
        }

        ReloadStage stage() {
            return this.stage;
        }

        ConfigFileIO.SaveReport report() {
            return Objects.requireNonNull(this.report);
        }
    }

    /**
     * Reloads the global configuration, the world defaults and the world configuration of the loaded levels.
     *
     * <p>Everything is read, migrated and checked first without touching the running configuration or any
     * file. Then the new values are applied, the module callbacks run until the first one fails, and only
     * after that the files are saved: world overrides that have to be brought up to date, the world defaults
     * and the global file. There is no rollback.</p>
     *
     * @return what was saved; a file that could not be written because access was denied is reported there
     * @throws ReloadException if the reload stopped
     */
    static ConfigFileIO.SaveReport reloadConfigs(List<LoadedLevel> levels) throws ReloadException {
        LeafGlobalConfig loadedGlobalConfig;
        LeafWorldConfig loadedWorldDefaults;
        List<ConfigBinder.PendingValue> candidateValues = new ArrayList<>();
        List<ConfigFile> overrideFilesToSave = new ArrayList<>();
        try {
            createDirectory(CONFIG_DIRECTORY);

            ConfigFile globalConfigFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, GLOBAL_CONFIG_FILE));
            ConfigFile worldDefaultsFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, DEFAULT_WORLD_CONFIG_FILE));
            VersionCheck globalVersion = globalVersion(globalConfigFile);
            // Migrations that were not saved, for example when access was denied, are run again. Gale is not
            // converted again, a Gale file whose values could not be saved is still there for the next start.
            LeafConfigMigration.migrate(globalConfigFile, worldDefaultsFile, migrationModuleClasses(), globalVersion.stored());

            loadedGlobalConfig = new LeafGlobalConfig(globalConfigFile, globalVersion);
            loadedWorldDefaults = reloadWorldDefaults(worldDefaultsFile, worldDefaultsConfig);

            for (ConfigModule module : GLOBAL_MODULES) {
                ConfigBinder.collectGlobalReload(module, loadedGlobalConfig, candidateValues);
            }
            collectWorldReloadValues(worldDefaultsConfig, loadedWorldDefaults, candidateValues);

            for (LoadedLevel level : levels) {
                LoadedWorld loadedWorld = loadWorldConfig(level.worldDirectory(), loadedWorldDefaults, true);
                collectWorldReloadValues(level.config(), loadedWorld.config(), candidateValues);
                if (loadedWorld.needsSave()) {
                    overrideFilesToSave.add(Objects.requireNonNull(loadedWorld.overrideFile()));
                }
            }
        } catch (Exception exception) {
            throw new ReloadException(ReloadStage.CANDIDATE, exception, null);
        }

        // Commit
        try {
            globalConfig = loadedGlobalConfig;
            worldDefaultsConfig.setConfigFile(loadedWorldDefaults.configFile);
            ConfigBinder.applyAll(candidateValues);

            runGlobalModuleCallbacks(true);
            runAfterBootstrapCallbacks(true);
        } catch (Exception exception) {
            throw new ReloadException(ReloadStage.CALLBACK, exception, null);
        }

        ConfigFileIO.SaveReport report = new ConfigFileIO.SaveReport(List.of());
        ConfigFileIO.ConfigSaveException failure = null;
        if (!overrideFilesToSave.isEmpty()) {
            try {
                report = ConfigFileIO.save(overrideFilesToSave.toArray(new ConfigFile[0]));
            } catch (ConfigFileIO.ConfigSaveException exception) {
                failure = exception;
                report = exception.report();
            }
        }
        try {
            report = ConfigFileIO.SaveReport.merge(report, saveGlobalFiles(worldDefaultsConfig, globalConfig));
        } catch (ConfigFileIO.ConfigSaveException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
            report = ConfigFileIO.SaveReport.merge(report, exception.report());
        }
        if (failure != null) {
            throw new ReloadException(ReloadStage.SAVE, (Exception) failure.getCause(), report);
        }
        return report;
    }

    /**
     * A world configuration, with the override file it was read from and whether that file has to be saved
     * because it was migrated or its version had to be brought up to date.
     */
    private record LoadedWorld(LeafWorldConfig config, @Nullable ConfigFile overrideFile, boolean needsSave) {
    }

    private static LoadedWorld loadWorldConfig(Path worldDirectory, LeafWorldConfig defaults, boolean migrateOverride) throws Exception {
        LeafWorldConfig config = new LeafWorldConfig(
            defaults.configFile,
            LeafWorldConfig.Source.WORLD_OVERRIDE
        );
        applyWorldDefaults(config, defaults);

        File worldConfigFile = worldDirectory.resolve(WORLD_CONFIG_FILE).toFile();
        if (!worldConfigFile.isFile()) {
            config.setConfigFile(defaults.configFile);
            return new LoadedWorld(config, null, false);
        }
        ConfigFile overrideConfig = ConfigFileIO.load(worldConfigFile);
        // An override is migrated by its own version. The global version says nothing about it.
        VersionCheck version = checkVersion(overrideConfig);
        boolean migrated = migrateOverride && LeafConfigMigration.migrateWorldOverride(
            overrideConfig,
            version.stored(),
            worldMigrationModuleClasses()
        );
        applyWorldOverride(config, overrideConfig, defaults);
        boolean needsSave = migrated || version.needsRewrite();
        if (needsSave) {
            overrideConfig.set(VERSION_PATH, version.valueToWrite());
        }
        return new LoadedWorld(config, overrideConfig, needsSave);
    }

    // Init config
    public static void loadConfig() {
        try {
            long begin = System.nanoTime();
            LOGGER.info("Loading config...");

            ConfigBackupSession backupSession = ConfigBackupSession.create(CONFIG_DIRECTORY.toPath());

            purgeOutdated(backupSession);
            createDirectory(CONFIG_DIRECTORY);

            ConfigFile globalConfigFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, GLOBAL_CONFIG_FILE));
            ConfigFile worldDefaultsFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, DEFAULT_WORLD_CONFIG_FILE));
            VersionCheck globalVersion = globalVersion(globalConfigFile);

            discoverGlobalModules();
            discoverWorldModules();

            // Migrate the same raw config instances that will be bound and saved below.
            LeafConfigMigration.migrate(globalConfigFile, worldDefaultsFile, migrationModuleClasses(), globalVersion.stored());
            GaleConfigMigration.migrate(
                CONFIG_DIRECTORY.toPath(),
                globalConfigFile,
                worldDefaultsFile,
                backupSession
            );

            globalConfig = new LeafGlobalConfig(globalConfigFile, globalVersion);
            for (ConfigModule module : GLOBAL_MODULES) {
                ConfigBinder.bindGlobal(module, globalConfig);
            }
            runGlobalModuleCallbacks(false);

            worldDefaultsConfig = loadWorldDefaults(worldDefaultsFile);
            captureWorldInitialReloadValues(worldDefaultsConfig);

            LOGGER.info("Successfully loaded config in {}ms.", (System.nanoTime() - begin) / 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Leaf configuration", e);
        }
    }

    /* Load Global Config */

    public static LeafGlobalConfig globalConfig() {
        return globalConfig;
    }

    public static LeafWorldConfig worldDefaultsConfig() {
        return worldDefaultsConfig;
    }

    /**
     * Creates a world configuration from the shared defaults and applies an existing override
     * without creating a file when no override exists.
     */
    public static LeafWorldConfig initWorldConfig(Path worldDirectory) {
        File worldConfigFile = worldDirectory.resolve(WORLD_CONFIG_FILE).toFile();
        LeafWorldConfig migratedConfig = GaleConfigMigration.migrateWorldOverride(
            worldDirectory,
            worldConfigFile,
            worldDefaultsConfig
        );
        if (migratedConfig != null) {
            try {
                captureWorldInitialReloadValues(migratedConfig);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            GaleConfigMigration.archiveWorldSource(worldDirectory);
            return migratedConfig;
        }
        try {
            LoadedWorld loaded = loadWorldConfig(worldDirectory, worldDefaultsConfig, true);
            if (loaded.needsSave()) {
                ConfigFileIO.save(loaded.overrideFile());
            }
            captureWorldInitialReloadValues(loaded.config());
            GaleConfigMigration.archiveWorldSource(worldDirectory);
            return loaded.config();
        } catch (Exception exception) {
            throw new RuntimeException("Could not load Leaf world config for " + worldDirectory, exception);
        }
    }

    private static void discoverGlobalModules() throws ReflectiveOperationException {
        Class<?>[] classes = getClasses(CONFIG_MODULE_PACKAGE).toArray(new Class[0]);
        ObjectArrays.quickSort(classes, Comparator.comparing((Class<?> clazz) -> clazz.getSimpleName())
            .thenComparing(Class::getName));
        for (Class<?> moduleClass : classes) {
            if (moduleClass.isInterface() || Modifier.isAbstract(moduleClass.getModifiers())) {
                continue;
            }

            if (!ConfigModule.class.isAssignableFrom(moduleClass)) {
                continue;
            }

            ConfigModule module = (ConfigModule) moduleClass.getConstructor().newInstance();
            GLOBAL_MODULES.add(module);
        }
    }

    private static void runGlobalModuleCallbacks(boolean reload) throws IllegalAccessException {
        List<Field> enabledExperimentalModules = new ArrayList<>();
        List<Field> deprecatedModules = new ArrayList<>();

        for (ConfigModule module : GLOBAL_MODULES) {
            Class<?> moduleClass = module.getClass();
            if (reload && moduleClass.isAnnotationPresent(HotReloadUnsupported.class)) {
                continue;
            }

            module.onLoaded();

            for (Field field : moduleClass.getDeclaredFields()) {
                boolean experimental = field.isAnnotationPresent(Experimental.class);
                boolean deprecated = field.isAnnotationPresent(Deprecated.class);
                if ((!experimental && !deprecated) || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.get(null) instanceof Boolean enabled && enabled) {
                    if (experimental) {
                        enabledExperimentalModules.add(field);
                    }
                    if (deprecated) {
                        deprecatedModules.add(field);
                    }
                }
            }
        }

        warnEnabledModules(
            enabledExperimentalModules,
            "You have following experimental module(s) enabled: {}, please proceed with caution!"
        );
        warnEnabledModules(
            deprecatedModules,
            "The following enabled module(s) has been deprecated: {}, please proceed with caution!"
        );
    }

    private static void collectWorldReloadValues(
        LeafWorldConfig config,
        LeafWorldConfig loadedConfig,
        List<ConfigBinder.PendingValue> pendingValues
    ) throws IllegalAccessException {
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule loadedModule = (WorldConfigModule) moduleField.get(loadedConfig);
            ConfigBinder.collectWorldReload(module, loadedModule, config.initialValues(), pendingValues);
        }
    }

    private static LeafWorldConfig loadWorldDefaults(ConfigFile configFile) throws ReflectiveOperationException {
        LeafWorldConfig config = new LeafWorldConfig(configFile, LeafWorldConfig.Source.WORLD_DEFAULTS);
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            ConfigBinder.bindWorldDefaults(module, module, config);
        }
        return config;
    }

    private static LeafWorldConfig reloadWorldDefaults(
        ConfigFile configFile,
        LeafWorldConfig activeDefaults
    ) throws ReflectiveOperationException {
        LeafWorldConfig config = new LeafWorldConfig(configFile, LeafWorldConfig.Source.WORLD_DEFAULTS);
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule activeModule = (WorldConfigModule) moduleField.get(activeDefaults);
            ConfigBinder.bindWorldDefaults(module, activeModule, config);
        }
        return config;
    }

    private static void discoverWorldModules() {
        Field[] fields = LeafWorldConfig.class.getDeclaredFields();
        ObjectArrays.quickSort(fields, Comparator.comparing((Field field) -> field.getType().getSimpleName())
            .thenComparing(field -> field.getType().getName()));
        for (Field field : fields) {
            if (WorldConfigModule.class.isAssignableFrom(field.getType())) {
                WORLD_MODULES.add(field);
            }
        }
    }

    private static List<Class<?>> migrationModuleClasses() {
        List<Class<?>> moduleClasses = new ArrayList<>(GLOBAL_MODULES.size() + WORLD_MODULES.size());
        for (ConfigModule module : GLOBAL_MODULES) {
            moduleClasses.add(module.getClass());
        }
        for (Field field : WORLD_MODULES) {
            moduleClasses.add(field.getType());
        }
        return moduleClasses;
    }

    private static List<Class<?>> worldMigrationModuleClasses() {
        List<Class<?>> moduleClasses = new ArrayList<>(WORLD_MODULES.size());
        for (Field field : WORLD_MODULES) {
            moduleClasses.add(field.getType());
        }
        return moduleClasses;
    }

    public static LeafWorldConfig loadWorldOverride(
        ConfigFile configFile,
        LeafWorldConfig worldDefaults
    ) throws ReflectiveOperationException {
        LeafWorldConfig config = new LeafWorldConfig(worldDefaults.configFile, LeafWorldConfig.Source.WORLD_OVERRIDE);
        applyWorldDefaults(config, worldDefaults);
        applyWorldOverride(config, configFile, worldDefaults);
        return config;
    }

    private static void applyWorldOverride(
        LeafWorldConfig config,
        ConfigFile configFile,
        LeafWorldConfig worldDefaults
    ) throws ReflectiveOperationException {
        config.setConfigFile(configFile);

        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule worldDefaultModule = (WorldConfigModule) moduleField.get(worldDefaults);
            ConfigBinder.bindWorldOverride(module, worldDefaultModule, config);
        }
    }

    public static void loadAfterBootstrap() {
        try {
            runAfterBootstrapCallbacks(false);
            for (ConfigModule module : GLOBAL_MODULES) {
                ConfigBinder.captureGlobalInitialValues(module);
            }
            ConfigFileIO.SaveReport report = saveGlobalFiles(worldDefaultsConfig, globalConfig);
            GaleConfigMigration.archiveGlobalSources(report);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to finish loading Leaf configuration", exception);
        }
    }

    /**
     * The version of the global configuration file, which records the migration progress of the global
     * configuration and the world defaults. A file that does not exist yet starts at the current version.
     */
    private static VersionCheck globalVersion(ConfigFile globalConfigFile) {
        return Files.isRegularFile(globalConfigFile.getFile().toPath())
            ? checkVersion(globalConfigFile)
            : VersionCheck.current();
    }

    /**
     * Saves the world defaults, then the global configuration. The global file records the version of both,
     * so when the defaults could not be saved the global file keeps the version it was loaded with: the
     * migrations of the defaults run again at the next start. Both files are always attempted.
     */
    private static ConfigFileIO.SaveReport saveGlobalFiles(
        LeafWorldConfig defaults,
        LeafGlobalConfig global
    ) throws ConfigFileIO.ConfigSaveException {
        ConfigFileIO.ConfigSaveException failure = null;
        ConfigFileIO.SaveReport defaultsReport;
        try {
            defaultsReport = ConfigFileIO.save(defaults.configFile);
        } catch (ConfigFileIO.ConfigSaveException exception) {
            failure = exception;
            defaultsReport = exception.report();
        }

        boolean defaultsSaved = failure == null && !defaultsReport.hasAccessDenied();
        Object versionToWrite = global.configFile.get(VERSION_PATH);
        if (!defaultsSaved) {
            global.configFile.set(VERSION_PATH, global.loadedVersion());
        }
        ConfigFileIO.SaveReport globalReport;
        try {
            globalReport = ConfigFileIO.save(global.configFile);
        } catch (ConfigFileIO.ConfigSaveException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
            globalReport = exception.report();
        } finally {
            global.configFile.set(VERSION_PATH, versionToWrite);
        }

        ConfigFileIO.SaveReport report = ConfigFileIO.SaveReport.merge(defaultsReport, globalReport);
        if (failure != null) {
            throw new ConfigFileIO.ConfigSaveException(report, (Exception) failure.getCause());
        }
        return report;
    }

    private static void runAfterBootstrapCallbacks(boolean reload) {
        for (ConfigModule module : GLOBAL_MODULES) {
            if (reload && module.getClass().isAnnotationPresent(HotReloadUnsupported.class)) {
                continue;
            }
            module.onRegistriesLoaded();
        }
    }

    private static void warnEnabledModules(List<Field> fields, String message) {
        if (fields.isEmpty()) {
            return;
        }
        LOGGER.warn(message, fields.stream()
            .map(field -> field.getDeclaringClass().getSimpleName() + "." + field.getName())
            .toList());
    }

    private static void applyWorldDefaults(
        LeafWorldConfig config,
        LeafWorldConfig defaults
    ) throws IllegalAccessException {
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule defaultsModule = (WorldConfigModule) moduleField.get(defaults);
            ConfigBinder.applyWorldDefaults(module, defaultsModule);
        }
    }

    private static void captureWorldInitialReloadValues(LeafWorldConfig config) throws IllegalAccessException {
        for (Field moduleField : WORLD_MODULES) {
            ConfigBinder.captureWorldInitialValues(moduleField.get(config), config);
        }
    }

    static boolean isChineseLocale() {
        return IS_CHINESE_LOCALE;
    }

    /* Create config folder */

    protected static void createDirectory(File dir) throws IOException {
        // Throws FileAlreadyExistsException if dir exists but is not a directory, like Paper does. The
        // file is not deleted, it may belong to the administrator.
        Files.createDirectories(dir.toPath());
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
     * Returns whether {@code storedVersion}, a version a configuration was last written with, predates
     * {@code version}. For example, {@code isConfigVersionBefore("3.0", "3.1")} is {@code true}.
     *
     * @param storedVersion a numeric dot-separated config version
     * @param version       a numeric dot-separated config version, such as {@code 3.1}
     * @throws IllegalArgumentException if a version is not a numeric dot-separated version
     */
    public static boolean isConfigVersionBefore(String storedVersion, String version) {
        return ConfigVersion.parse(storedVersion).compareTo(ConfigVersion.parse(version)) < 0;
    }

    /**
     * Returns whether {@code storedVersion}, a version a configuration was last written with, is not older
     * than {@code version}.
     *
     * @throws IllegalArgumentException if a version is not a numeric dot-separated version
     */
    public static boolean isConfigVersionAtLeast(String storedVersion, String version) {
        return !isConfigVersionBefore(storedVersion, version);
    }

    /**
     * The version of a configuration file that exists, checked against {@link #CURRENT_CONFIG_VERSION}.
     *
     * @param stored       the version that decides which migrations still have to run
     * @param valueToWrite the {@code config-version} value to save: the current version, or the file's own
     *                     version when it is newer than the supported one
     * @param needsRewrite whether the file has to be saved to bring its version up to date
     */
    record VersionCheck(ConfigVersion stored, Object valueToWrite, boolean needsRewrite) {

        static VersionCheck current() {
            return new VersionCheck(ConfigVersion.parse(CURRENT_CONFIG_VERSION), CURRENT_CONFIG_VERSION, false);
        }
    }

    /**
     * Reads the {@code config-version} of an existing raw file.
     *
     * <p>A file without a version is read as the current version: it is warned about, not migrated and
     * written back with the version. A version that cannot be parsed is rejected. A version newer than the
     * supported one is warned about, not migrated and keeps its version when saved.</p>
     *
     * @throws IllegalArgumentException if the version cannot be parsed
     */
    static VersionCheck checkVersion(ConfigFile file) {
        VersionCheck current = VersionCheck.current();
        Object raw = file.get(VERSION_PATH);
        if (raw == null) {
            LOGGER.warn("{} has no {}; reading it as the current version {} without migrating it. The version is added when the file is saved.",
                file.getFile(), VERSION_PATH, CURRENT_CONFIG_VERSION);
            return new VersionCheck(current.stored(), CURRENT_CONFIG_VERSION, true);
        }

        ConfigVersion stored;
        try {
            stored = ConfigVersion.parse(String.valueOf(raw));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + VERSION_PATH + " '" + raw + "' in " + file.getFile(), exception);
        }

        int comparison = stored.compareTo(current.stored());
        if (comparison > 0) {
            LOGGER.warn("{} was written by a newer Leaf version (config-version {}, this server supports up to {}). It is not migrated and keeps its version, options that this version does not know are dropped when it is saved.",
                file.getFile(), raw, CURRENT_CONFIG_VERSION);
            return new VersionCheck(stored, raw, false);
        }
        return new VersionCheck(stored, CURRENT_CONFIG_VERSION, comparison < 0);
    }


    /* Register Spark profiler extra server configurations */

    private static List<String> buildSparkExtraConfigs() {
        List<String> extraConfigs = new ArrayList<>(Arrays.asList(
            "config/leaf-global.yml",
            "config/leaf-world-defaults.yml"
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
            Path leafWorldFile = world.getWorldFolder().toPath().resolve(WORLD_CONFIG_FILE);
            extraConfigs.add(leafWorldFile.toString().replace("\\", "/").replace("./", "")); // Leaf world override config
        }

        return extraConfigs;
    }

    private static List<String> buildSparkHiddenPaths() {
        List<String> extraHidden = new ArrayList<>();

        String existing = System.getProperty(SPARK_HIDDEN_PATHS_PROPERTY);
        if (existing != null) {
            extraHidden.addAll(Arrays.asList(existing.split(",")));
        }

        extraHidden.add(SentryDSN.sentryDsnConfigPath); // Hide Sentry DSN key

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

    private static boolean moveLegacy(ConfigBackupSession backupSession, File file, String name) {
        try {
            backupSession.move(file.toPath(), Path.of(name));
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not move legacy config {} to the backup directory, it is left in place.", file, exception);
            return false;
        }
    }

    private static void purgeOutdated(ConfigBackupSession backupSession) {
        boolean legacyFound = false;
        String pufferfishConfig = "pufferfish.yml";
        String leafConfigV1 = "leaf.yml";
        String leafConfigV2 = "leaf_config";

        File pufferfishConfigFile = new File(pufferfishConfig);
        File leafConfigV1File = new File(leafConfigV1);
        File leafConfigV2File = new File(leafConfigV2);

        // Only moves the legacy files, their content is not converted. A file that cannot be moved stays
        // in place and is tried again at the next start, so a failure only warns.
        if (pufferfishConfigFile.isFile()) {
            legacyFound |= moveLegacy(backupSession, pufferfishConfigFile, pufferfishConfig);
        }
        if (leafConfigV1File.isFile()) {
            legacyFound |= moveLegacy(backupSession, leafConfigV1File, leafConfigV1);
        }
        if (leafConfigV2File.isDirectory()) {
            legacyFound |= moveLegacy(backupSession, leafConfigV2File, leafConfigV2);
        }
        if (legacyFound) {
            LOGGER.warn("Found legacy Leaf config files, moved to backup directory: {}", backupSession.directory());
            LOGGER.warn("New Leaf config located at config/ folder, You need to transfer config to the new one manually and restart the server!");
        }
    }
}
