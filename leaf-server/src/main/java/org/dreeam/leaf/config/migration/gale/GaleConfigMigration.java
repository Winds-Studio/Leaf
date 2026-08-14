package org.dreeam.leaf.config.migration.gale;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.modules.gameplay.BookWriting;
import org.dreeam.leaf.config.modules.opt.SaveFireworks;
import org.dreeam.leaf.config.util.ConfigPaths;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String GLOBAL_FILE = "gale-global.yml";
    private static final String WORLD_DEFAULTS_FILE = "gale-world-defaults.yml";
    private static final String WORLD_OVERRIDE_FILE = "gale-world.yml";
    private static final Set<String> IGNORED_PATHS = Set.of("_version");

    private static Path configDirectory;
    private static List<Mapping> globalMappings = List.of();
    private static List<Mapping> worldMappings = List.of();
    private static Map<String, String> resolvedWorldMappings = Map.of();
    private static PendingMigration globalMigration;
    private static PendingMigration worldDefaultsMigration;
    private static Path backupDirectory;

    public static void migrate(Path directory, ConfigFile leafGlobalConfig, ConfigFile leafWorldDefaultsConfig) {
        configDirectory = Objects.requireNonNull(directory, "directory").normalize();
        backupDirectory = null;
        globalMigration = null;
        worldDefaultsMigration = null;

        Path globalPath = configDirectory.resolve(GLOBAL_FILE);
        Path worldDefaultsPath = configDirectory.resolve(WORLD_DEFAULTS_FILE);

        registerMappings();

        Map<String, String> resolvedGlobalMappings = resolveMappings(globalMappings, true);
        resolvedWorldMappings = resolveMappings(worldMappings, false);
        globalMigration = collectMigration(globalPath, resolvedGlobalMappings);
        worldDefaultsMigration = collectMigration(worldDefaultsPath, resolvedWorldMappings);

        applyMigration(globalMigration, leafGlobalConfig);
        applyMigration(worldDefaultsMigration, leafWorldDefaultsConfig);
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

    public static void finalizeGlobalMigration() {
        finalizeMigration(globalMigration);
        globalMigration = null;
    }

    public static void finalizeWorldDefaultsMigration() {
        finalizeMigration(worldDefaultsMigration);
        worldDefaultsMigration = null;
    }

    /**
     * Attempts to create a Leaf override from Gale values.
     *
     * @return the newly created Leaf override, or {@code null} when normal Leaf loading should continue
     */
    public static @Nullable LeafWorldConfig migrateWorldOverride(Path worldDirectory, File leafFile, LeafWorldConfig defaults) {
        Path leafPath = leafFile.toPath();
        Path galePath = worldDirectory.resolve(WORLD_OVERRIDE_FILE);

        if (!Files.isRegularFile(galePath)) {
            return null;
        }

        boolean leafFileCreated = false;
        try {
            if (LeafWorldConfig.exists(leafFile)) {
                LOGGER.warn(
                    "Could not migrate Gale world config {} because Leaf world override {} already exists.",
                    galePath, leafPath
                );
                return null;
            }

            PendingMigration migration = collectMigration(galePath, resolvedWorldMappings);
            if (migration == null || !migration.hasValues()) {
                return null;
            }

            Files.createFile(leafPath);
            leafFileCreated = true;
            ConfigFile leafConfig = ConfigFile.loadConfig(leafFile);
            applyMigration(migration, leafConfig);
            LeafWorldConfig migrated = LeafWorldConfig.loadOverride(leafConfig, defaults);
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
        } finally {
            archiveWorldOverride(galePath, worldDirectory);
            LOGGER.warn(
                "Finished processing Gale world config for {}. Please manually check the Leaf configuration used by this world.",
                worldDirectory
            );
        }
    }

    private static void applyMigration(@Nullable PendingMigration migration, ConfigFile leafConfig) {
        if (migration == null) {
            return;
        }
        migration.values().forEach(leafConfig::set);
    }

    private static void finalizeMigration(@Nullable PendingMigration migration) {
        if (migration == null) {
            return;
        }
        archive(migration);
    }

    private static Map<String, String> resolveMappings(List<Mapping> mappings, boolean global) {
        Map<String, String> resolvedMappings = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            String oldPath = mapping.oldPath();
            if (oldPath.isBlank()) {
                throw new IllegalArgumentException("Gale config path must not be blank");
            }

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

    private static synchronized Path backupDirectory() throws IOException {
        if (backupDirectory != null) {
            return backupDirectory;
        }
        Path root = configDirectory.resolve("backup");
        Files.createDirectories(root);
        String name = "backup-" + BACKUP_TIME_FORMAT.format(LocalDateTime.now());
        for (int counter = 0; ; counter++) {
            Path candidate = root.resolve(counter == 0 ? name : name + '-' + counter);
            try {
                Files.createDirectory(candidate);
                backupDirectory = candidate;
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
            }
        }
    }

    private static @Nullable PendingMigration collectMigration(
        Path sourcePath,
        Map<String, String> mappings
    ) {
        if (!Files.isRegularFile(sourcePath)) {
            return null;
        }
        try {
            ConfigFile config = ConfigFile.loadConfig(sourcePath.toFile());
            boolean hasValues = hasConfigValues(config, "");
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String oldPath = entry.getKey();
                if (!config.contains(oldPath)) {
                    continue;
                }
                // Gale has already validated the source option; migration preserves its raw value.
                values.put(entry.getValue(), config.get(oldPath));
            }
            return new PendingMigration(
                sourcePath,
                Map.copyOf(values),
                hasValues
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to read Gale config {}; migration was skipped.", sourcePath, exception);
            return null;
        }
    }

    private static void archive(PendingMigration migration) {
        try {
            Path backupPath = Path.of(migration.sourcePath().getFileName().toString());
            moveToBackup(migration.sourcePath(), backupPath);
        } catch (IOException exception) {
            logBackupFailure(migration.sourcePath(), exception);
        }
    }

    private static void archiveWorldOverride(Path sourcePath, Path worldDirectory) {
        try {
            Path fileName = Path.of(sourcePath.getFileName().toString());
            Path backupPath = Path.of("world-overrides").resolve(worldContext(worldDirectory)).resolve(fileName);
            moveToBackup(sourcePath, backupPath);
        } catch (IOException exception) {
            logBackupFailure(sourcePath, exception);
        }
    }

    private static void moveToBackup(Path sourcePath, Path backupPath) throws IOException {
        Path relative = backupPath.normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Invalid Gale backup path: " + relative);
        }
        Path target = backupDirectory().resolve(relative).normalize();
        Files.createDirectories(target.getParent());
        Files.move(sourcePath, target);
        LOGGER.warn("Moved Gale config {} to {}.", sourcePath, target);
    }

    private static void logBackupFailure(Path sourcePath, IOException exception) {
        LOGGER.error("Failed to back up Gale config {}; leaving it in place.", sourcePath, exception);
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

    private record PendingMigration(
        Path sourcePath,
        Map<String, Object> values,
        boolean hasValues
    ) {
    }
}
