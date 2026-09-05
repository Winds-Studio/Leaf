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
import org.jspecify.annotations.NullMarked;
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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

    private static ConfigVersion previousConfigVersion = ConfigVersion.init();
    private static final AtomicBoolean RELOAD_QUEUED_OR_RUNNING = new AtomicBoolean();
    private static final ReentrantLock RELOAD_TRANSACTION_LOCK = new ReentrantLock();

    /* Load & Reload */

    // Reload config on the server thread
    public static CompletableFuture<Void> reloadAsync(CommandSender sender) {
        if (!RELOAD_QUEUED_OR_RUNNING.compareAndSet(false, true)) {
            Command.broadcastCommandMessage(sender, Component.text("Leaf config reload is already in progress.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(null);
        }
        MinecraftServer server = MinecraftServer.getServer();
        try {
            return CompletableFuture.runAsync(() -> {
                RELOAD_TRANSACTION_LOCK.lock();
                try {
                    long begin = System.nanoTime();

                    createDirectory(CONFIG_DIRECTORY);

                    ConfigFile globalConfigFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, GLOBAL_CONFIG_FILE));
                    ConfigFile worldDefaultsFile = ConfigFileIO.load(new File(CONFIG_DIRECTORY, DEFAULT_WORLD_CONFIG_FILE));
                    LeafGlobalConfig loadedGlobalConfig = new LeafGlobalConfig(globalConfigFile, false);
                    LeafWorldConfig loadedWorldDefaults = reloadWorldDefaults(worldDefaultsFile, worldDefaultsConfig);

                    List<ConfigBinder.ReloadValue> candidateValues = new ArrayList<>();
                    for (ConfigModule module : GLOBAL_MODULES) {
                        ConfigBinder.collectGlobalReload(module, loadedGlobalConfig, candidateValues);
                    }
                    collectWorldReloadValues(worldDefaultsConfig, loadedWorldDefaults, candidateValues);

                    List<WorldReload> worldReloads = new ArrayList<>();
                    for (ServerLevel level : server.getAllLevels()) {
                        Path worldDirectory = server.storageSource.getDimensionPath(level.dimension());
                        LeafWorldConfig loadedConfig = loadWorldConfig(worldDirectory, loadedWorldDefaults, false);
                        LeafWorldConfig currentConfig = level.leafConfig();
                        collectWorldReloadValues(currentConfig, loadedConfig, candidateValues);
                        worldReloads.add(new WorldReload(currentConfig, loadedConfig.configFile));
                    }

                    ReloadCommit commit = commitReload(loadedGlobalConfig, loadedWorldDefaults, worldReloads, candidateValues);
                    ConfigFileIO.SaveReport saveReport = commit.saveReport();

                    final String success = String.format("Successfully reloaded config in %sms.", (System.nanoTime() - begin) / 1_000_000);
                    Command.broadcastCommandMessage(sender, Component.text(success, NamedTextColor.GREEN));
                    if (saveReport.hasAccessDenied()) {
                        Command.broadcastCommandMessage(sender, Component.text(
                            "Leaf config reload is active in memory, but could not persist: " + saveReport.describe(),
                            NamedTextColor.YELLOW
                        ));
                    }
                    if (commit.callbackFailure() != null) {
                        Command.broadcastCommandMessage(sender, Component.text(
                            "Leaf config was reloaded, but post-reload processing failed. See error in console.",
                            NamedTextColor.YELLOW
                        ));
                    }
                } catch (ConfigFileIO.ConfigSaveException exception) {
                    Command.broadcastCommandMessage(sender, Component.text(
                        "Leaf config reload is active in memory, but persistence failed: " + exception.report().describe(),
                        NamedTextColor.RED
                    ));
                    LOGGER.error("Leaf config reload persistence failed: {}", exception.report().describe(), exception);
                } catch (Exception e) {
                    Command.broadcastCommandMessage(sender, Component.text("Failed to reload config. See error in console!", NamedTextColor.RED));
                    LOGGER.error("Failed to reload config!", e);
                } finally {
                    RELOAD_TRANSACTION_LOCK.unlock();
                    RELOAD_QUEUED_OR_RUNNING.set(false);
                }
            }, server);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            RELOAD_QUEUED_OR_RUNNING.set(false);
            Command.broadcastCommandMessage(sender, Component.text("Leaf config reload could not be scheduled.", NamedTextColor.RED));
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static LeafWorldConfig loadWorldConfig(Path worldDirectory, LeafWorldConfig defaults, boolean migrateOverride) throws Exception {
        LeafWorldConfig config = new LeafWorldConfig(
            defaults.configFile,
            LeafWorldConfig.Source.WORLD_OVERRIDE
        );
        applyWorldDefaults(config, defaults);

        File worldConfigFile = worldDirectory.resolve(WORLD_CONFIG_FILE).toFile();
        if (!worldConfigFile.isFile()) {
            config.setConfigFile(defaults.configFile);
            return config;
        }
        ConfigFile overrideConfig = ConfigFileIO.load(worldConfigFile);
        boolean migrated = migrateOverride && LeafConfigMigration.migrateWorldOverride(
            overrideConfig,
            worldMigrationModuleClasses()
        );
        applyWorldOverride(config, overrideConfig, defaults);
        if (migrated) {
            ConfigFileIO.save(overrideConfig);
        }
        return config;
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

            discoverGlobalModules();
            discoverWorldModules();

            // Migrate the same raw config instances that will be bound and saved below.
            LeafConfigMigration.migrate(globalConfigFile, worldDefaultsFile, migrationModuleClasses());
            GaleConfigMigration.migrate(
                CONFIG_DIRECTORY.toPath(),
                globalConfigFile,
                worldDefaultsFile,
                backupSession
            );

            globalConfig = new LeafGlobalConfig(globalConfigFile, true);
            for (ConfigModule module : GLOBAL_MODULES) {
                ConfigBinder.bind(module, null, globalConfig, true);
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
            return migratedConfig;
        }
        try {
            LeafWorldConfig config = loadWorldConfig(worldDirectory, worldDefaultsConfig, true);
            captureWorldInitialReloadValues(config);
            return config;
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
            ConfigBinder.registerGlobalDefaults(module);
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
        List<ConfigBinder.ReloadValue> pendingValues
    ) throws IllegalAccessException {
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule loadedModule = (WorldConfigModule) moduleField.get(loadedConfig);
            ConfigBinder.collectWorldReload(module, loadedModule, pendingValues);
        }
    }

    private static ReloadCommit commitReload(
        LeafGlobalConfig loadedGlobalConfig,
        LeafWorldConfig loadedWorldDefaults,
        List<WorldReload> worldReloads,
        List<ConfigBinder.ReloadValue> candidateValues
    ) throws Exception {
        List<ConfigBinder.PendingValue> pendingValues = ConfigBinder.preparePendingValues(candidateValues);

        globalConfig = loadedGlobalConfig;
        worldDefaultsConfig.setConfigFile(loadedWorldDefaults.configFile);
        for (WorldReload worldReload : worldReloads) {
            worldReload.config().setConfigFile(worldReload.loadedConfigFile());
        }
        for (ConfigBinder.PendingValue pendingValue : pendingValues) {
            pendingValue.apply();
        }

        ConfigFileIO.SaveReport saveReport = ConfigFileIO.save(worldDefaultsConfig.configFile, globalConfig.configFile);
        Exception callbackFailure = null;
        try {
            runGlobalModuleCallbacks(true);
            runAfterBootstrapCallbacks(true);
        } catch (Exception exception) {
            callbackFailure = exception;
            LOGGER.error("Leaf config reload post-processing failed after configuration was committed.", exception);
        }
        return new ReloadCommit(saveReport, callbackFailure);
    }

    private static LeafWorldConfig loadWorldDefaults(ConfigFile configFile) throws ReflectiveOperationException {
        discoverWorldModules();

        LeafWorldConfig config = new LeafWorldConfig(configFile, LeafWorldConfig.Source.WORLD_DEFAULTS);
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            ConfigBinder.bind(module, null, config, false);
        }
        return config;
    }

    private static LeafWorldConfig reloadWorldDefaults(
        ConfigFile configFile,
        LeafWorldConfig activeDefaults
    ) throws ReflectiveOperationException {
        discoverWorldModules();
        LeafWorldConfig config = new LeafWorldConfig(configFile, LeafWorldConfig.Source.WORLD_DEFAULTS);
        for (Field moduleField : WORLD_MODULES) {
            WorldConfigModule module = (WorldConfigModule) moduleField.get(config);
            WorldConfigModule activeModule = (WorldConfigModule) moduleField.get(activeDefaults);
            ConfigBinder.bindWorldDefaultsForReload(module, activeModule, config);
        }
        return config;
    }

    private static void discoverWorldModules() {
        if (!WORLD_MODULES.isEmpty()) {
            return;
        }
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
            ConfigBinder.bind(module, worldDefaultModule, config, false);
        }
    }

    public static void loadAfterBootstrap() {
        try {
            runAfterBootstrapCallbacks(false);
            for (ConfigModule module : GLOBAL_MODULES) {
                ConfigBinder.captureInitialReloadValues(module, true);
            }
            ConfigFileIO.SaveReport report = ConfigFileIO.save(worldDefaultsConfig.configFile, globalConfig.configFile);
            GaleConfigMigration.recordLeafPersistence(report);
        } catch (Exception exception) {
            LOGGER.error("Failed to save config file!", exception);
            throw new IllegalStateException("Failed to finish loading Leaf configuration", exception);
        }
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
            ConfigBinder.captureInitialReloadValues(moduleField.get(config), false);
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
        previousConfigVersion = parseStoredConfigVersion(previousVersion, true);
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

    public static boolean isConfigVersionBefore(String storedVersion, String version) {
        return parseStoredConfigVersion(storedVersion, false).compareTo(ConfigVersion.parse(version)) < 0;
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

    private static ConfigVersion parseStoredConfigVersion(String version, boolean warnIfInvalid) {
        if (version == null) {
            return ConfigVersion.init();
        }

        try {
            return ConfigVersion.parse(version);
        } catch (IllegalArgumentException exception) {
            if (warnIfInvalid) {
                LOGGER.warn("Invalid Leaf config version '{}'; treating it as an unversioned configuration.", version);
            }
            return ConfigVersion.init();
        }
    }

    private record WorldReload(
        LeafWorldConfig config,
        ConfigFile loadedConfigFile
    ) {
    }

    private record ReloadCommit(ConfigFileIO.SaveReport saveReport, Exception callbackFailure) {
    }

    private record ConfigVersion(List<Integer> components) implements Comparable<ConfigVersion> {

        private static ConfigVersion init() {
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

    private static void purgeOutdated(ConfigBackupSession backupSession) throws IOException {
        boolean legacyFound = false;
        String pufferfishConfig = "pufferfish.yml";
        String leafConfigV1 = "leaf.yml";
        String leafConfigV2 = "leaf_config";

        File pufferfishConfigFile = new File(pufferfishConfig);
        File leafConfigV1File = new File(leafConfigV1);
        File leafConfigV2File = new File(leafConfigV2);

        if (pufferfishConfigFile.isFile()) {
            legacyFound |= backupSession.move(pufferfishConfigFile.toPath(), Path.of(pufferfishConfig));
        }
        if (leafConfigV1File.isFile()) {
            legacyFound |= backupSession.move(leafConfigV1File.toPath(), Path.of(leafConfigV1));
        }
        if (leafConfigV2File.isDirectory()) {
            legacyFound |= backupSession.move(leafConfigV2File.toPath(), Path.of(leafConfigV2));
        }
        if (legacyFound) {
            LOGGER.warn("Found legacy Leaf config files, moved to backup directory: {}", backupSession.directory());
            LOGGER.warn("New Leaf config located at config/ folder, You need to transfer config to the new one manually and restart the server!");
        }
    }
}
