package org.dreeam.leaf.config.migration.gale;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.modules.gameplay.BookWriting;
import org.dreeam.leaf.config.modules.opt.SaveFireworks;
import org.dreeam.leaf.config.util.ConfigPaths;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Migrates the removed Gale configuration files into Leaf configuration modules.
 */
public final class GaleConfigMigration {

    private static final Logger LOGGER = LogManager.getLogger(GaleConfigMigration.class.getSimpleName());
    private static final String GLOBAL_FILE = "gale-global.yml";
    private static final String WORLD_DEFAULTS_FILE = "gale-world-defaults.yml";
    private static final String WORLD_OVERRIDE_FILE = "gale-world.yml";
    private static final Set<String> IGNORED_PATHS = Set.of("_version");

    private static Path configDirectory;
    private static List<Mapping> globalMappings = List.of();
    private static List<Mapping> worldMappings = List.of();
    private static Map<String, String> resolvedWorldMappings = Map.of();

    public static void migrate(Path directory, ConfigFile leafGlobalConfig, ConfigFile leafWorldDefaultsConfig) {
        configDirectory = Objects.requireNonNull(directory, "directory").normalize();

        Path globalPath = configDirectory.resolve(GLOBAL_FILE);
        Path worldDefaultsPath = configDirectory.resolve(WORLD_DEFAULTS_FILE);

        registerMappings();
        Map<String, String> resolvedGlobalMappings = resolveMappings(globalMappings);
        resolvedWorldMappings = resolveMappings(worldMappings);
        migrateValues(globalPath, resolvedGlobalMappings, leafGlobalConfig);
        migrateValues(worldDefaultsPath, resolvedWorldMappings, leafWorldDefaultsConfig);
    }

    private static void registerMappings() {
        globalMappings = new ArrayList<>();
        worldMappings = new ArrayList<>();

        addGlobalMapping("gameplay-mechanics.enable-book-writing", BookWriting.class, "enabled");

        addWorldMapping("small-optimizations.save-fireworks", SaveFireworks.class, "enabled");
    }

    private static void addGlobalMapping(String oldPath, Class<? extends ConfigModule> moduleClass, String fieldName) {
        globalMappings.add(new Mapping(oldPath, moduleClass, fieldName));
    }

    private static void addWorldMapping(String oldPath, Class<? extends WorldConfigModule> moduleClass, String fieldName) {
        worldMappings.add(new Mapping(oldPath, moduleClass, fieldName));
    }

    public static void finalizeMigration(MinecraftServer server) {
        if (configDirectory == null) {
            return;
        }

        boolean galeConfigFound = archive(configDirectory.resolve(GLOBAL_FILE));
        galeConfigFound |= archive(configDirectory.resolve(WORLD_DEFAULTS_FILE));
        for (ServerLevel level : server.getAllLevels()) {
            Path worldDirectory = server.storageSource.getDimensionPath(level.dimension());
            galeConfigFound |= archiveWorldOverride(worldDirectory.resolve(WORLD_OVERRIDE_FILE), worldDirectory);
        }

        if (galeConfigFound) {
            LOGGER.warn(
                "Gale configuration migration has finished. Please manually check the migrated Leaf configuration files for correctness."
            );
        }
    }

    /**
     * Attempts to create a Leaf override from Gale values.
     *
     * @return the newly created Leaf override, or {@code null} when normal Leaf loading should continue
     */
    public static @Nullable LeafWorldConfig migrateWorldOverride(
        Path worldDirectory,
        File leafFile,
        LeafWorldConfig defaults
    ) {
        Path leafPath = leafFile.toPath();
        Path galePath = worldDirectory.resolve(WORLD_OVERRIDE_FILE);

        if (!Files.isRegularFile(galePath)) {
            return null;
        }

        boolean leafFileCreated = false;
        try {
            if (leafFile.isFile()) {
                LOGGER.warn(
                    "Could not migrate Gale world config {} because Leaf world override {} already exists.",
                    galePath, leafPath
                );
                return null;
            }

            ConfigFile galeConfig = loadSrcConfig(galePath);
            if (galeConfig == null || !hasConfigValues(galeConfig, "")) {
                return null;
            }

            Files.createFile(leafPath);
            leafFileCreated = true;
            ConfigFile leafConfig = ConfigFile.loadConfig(leafFile);
            applyMappings(galeConfig, resolvedWorldMappings, leafConfig);
            LeafWorldConfig migrated = LeafConfig.loadWorldOverride(leafConfig, defaults);
            migrated.saveConfig();
            return migrated;
        } catch (Exception exception) {
            if (leafFileCreated) {
                try {
                    Files.deleteIfExists(leafPath);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            LOGGER.error(
                "Failed to migrate Gale world config {} for {}; using Leaf world defaults.",
                galePath, worldDirectory, exception
            );
            return null;
        }
    }

    private static void migrateValues(Path srcPath, Map<String, String> mappings, ConfigFile leafConfig) {
        ConfigFile srcConfig = loadSrcConfig(srcPath);
        if (srcConfig == null) {
            return;
        }
        applyMappings(srcConfig, mappings, leafConfig);
    }

    private static void applyMappings(
        ConfigFile srcConfig,
        Map<String, String> mappings,
        ConfigFile leafConfig
    ) {
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String oldPath = entry.getKey();
            if (srcConfig.contains(oldPath)) {
                leafConfig.set(entry.getValue(), srcConfig.get(oldPath));
            }
        }
    }

    private static Map<String, String> resolveMappings(List<Mapping> mappings) {
        Map<String, String> resolvedMappings = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            String oldPath = mapping.oldPath();
            Field field;
            try {
                field = mapping.moduleClass().getDeclaredField(mapping.fieldName());
            } catch (NoSuchFieldException exception) {
                throw new IllegalArgumentException("Invalid Gale migration target "
                    + mapping.moduleClass().getName() + '.' + mapping.fieldName(), exception);
            }

            String leafPath = ConfigPaths.fieldPath(mapping.moduleClass(), field);

            resolvedMappings.put(oldPath, leafPath);
        }
        return resolvedMappings;
    }

    private static Path worldContext(Path worldDirectory) {
        Path absolute = worldDirectory.toAbsolutePath().normalize();
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        return absolute.startsWith(workingDirectory)
            ? workingDirectory.relativize(absolute)
            : absolute.subpath(0, absolute.getNameCount());
    }

    private static @Nullable ConfigFile loadSrcConfig(Path srcPath) {
        if (!Files.isRegularFile(srcPath)) {
            return null;
        }
        try {
            return ConfigFile.loadConfig(srcPath.toFile());
        } catch (Exception exception) {
            LOGGER.error("Failed to read Gale config {}; migration was skipped.", srcPath, exception);
            return null;
        }
    }

    private static boolean archive(Path srcPath) {
        if (!Files.isRegularFile(srcPath)) return false;
        try {
            Path backupPath = Path.of(srcPath.getFileName().toString());
            moveToBackup(srcPath, backupPath);
        } catch (IOException exception) {
            LOGGER.error("Failed to back up Gale config {}; leaving it in place.", srcPath, exception);
        }
        return true;
    }

    private static boolean archiveWorldOverride(Path srcPath, Path worldDirectory) {
        if (!Files.isRegularFile(srcPath)) return false;
        try {
            Path fileName = Path.of(srcPath.getFileName().toString());
            Path backupPath = Path.of("world-overrides").resolve(worldContext(worldDirectory)).resolve(fileName);
            moveToBackup(srcPath, backupPath);
        } catch (IOException exception) {
            LOGGER.error("Failed to back up Gale config {}; leaving it in place.", srcPath, exception);
        }
        return true;
    }

    private static void moveToBackup(Path srcPath, Path backupPath) throws IOException {
        Path relative = backupPath.normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Invalid Gale backup path: " + relative);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddhhmmss");
        Path backupDirectory = configDirectory.resolve("backup" + dateFormat.format(new Date()));
        Path target = backupDirectory.resolve(relative).normalize();
        Files.createDirectories(target.getParent());
        Files.move(srcPath, target);
        LOGGER.warn("Moved Gale config {} to {}.", srcPath, target);
    }

    private static boolean hasConfigValues(Map<?, ?> values, String parent) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String path = parent.isEmpty() ? name : parent + '.' + name;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                if (hasConfigValues(nested, path)) {
                    return true;
                }
            } else if (!IGNORED_PATHS.contains(path)) {
                return true;
            }
        }
        return false;
    }

    private record Mapping(String oldPath, Class<?> moduleClass, String fieldName) {
    }
}
