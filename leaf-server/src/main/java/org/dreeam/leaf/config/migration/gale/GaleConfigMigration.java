package org.dreeam.leaf.config.migration.gale;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigPathResolver;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.modules.gameplay.BookWriting;
import org.dreeam.leaf.config.modules.opt.SaveFireworks;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Migrates the removed Gale configuration files into Leaf configuration modules. */
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
    private static Map<String, ResolvedTarget> resolvedWorldMappings = Map.of();
    private static PendingMigration globalMigration;
    private static PendingMigration worldDefaultsMigration;
    private static Path backupDirectory;
    private static boolean migrationEnabled;

    private GaleConfigMigration() {
    }

    public static void migrate(Path directory, ConfigFile leafGlobalConfig, ConfigFile leafWorldDefaultsConfig) {
        configDirectory = Objects.requireNonNull(directory, "directory").normalize();
        backupDirectory = null;
        globalMigration = null;
        worldDefaultsMigration = null;

        Path globalPath = configDirectory.resolve(GLOBAL_FILE);
        Path worldDefaultsPath = configDirectory.resolve(WORLD_DEFAULTS_FILE);
        migrationEnabled = Files.isRegularFile(globalPath) || Files.isRegularFile(worldDefaultsPath);
        if (!migrationEnabled) {
            globalMappings = List.of();
            worldMappings = List.of();
            resolvedWorldMappings = Map.of();
            return;
        }

        registerMappings();
        Map<String, ResolvedTarget> resolvedGlobalMappings = resolveMappings(globalMappings, true);
        resolvedWorldMappings = resolveMappings(worldMappings, false);
        globalMigration = collectMigration(globalPath, Path.of(""), resolvedGlobalMappings);
        worldDefaultsMigration = collectMigration(worldDefaultsPath, Path.of(""), resolvedWorldMappings);
        migrateValues(globalMigration, leafGlobalConfig);
        migrateValues(worldDefaultsMigration, leafWorldDefaultsConfig);
    }

    private static void registerMappings() {
        globalMappings = new ArrayList<>();
        worldMappings = new ArrayList<>();

        addGlobalMapping("gameplay-mechanics.enable-book-writing", BookWriting.class, "enabled");

        addWorldMapping("small-optimizations.save-fireworks", SaveFireworks.class, "enabled");

        globalMappings = List.copyOf(globalMappings);
        worldMappings = List.copyOf(worldMappings);
    }

    private static void addGlobalMapping(
        String oldPath,
        Class<?> moduleClass,
        String fieldName
    ) {
        globalMappings.add(new Mapping(oldPath, moduleClass, fieldName));
    }

    private static void addWorldMapping(
        String oldPath,
        Class<?> moduleClass,
        String fieldName
    ) {
        worldMappings.add(new Mapping(oldPath, moduleClass, fieldName));
    }

    public static void completeGlobal(Path leafFile) {
        complete(globalMigration, leafFile);
        globalMigration = null;
    }

    public static void completeWorldDefaults(Path leafFile) {
        complete(worldDefaultsMigration, leafFile);
        worldDefaultsMigration = null;
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
        if (!migrationEnabled) {
            return null;
        }

        Path leafPath = leafFile.toPath();
        Path galePath = worldDirectory.resolve(WORLD_OVERRIDE_FILE);

        if (LeafWorldConfig.exists(leafFile)) {
            if (Files.isRegularFile(galePath)) {
                renameOverride(galePath, "A Leaf world override already exists for " + worldDirectory + '.');
            }
            return null;
        }
        if (!Files.isRegularFile(galePath)) {
            return null;
        }

        PendingMigration migration = collectMigration(
            galePath,
            worldContext(worldDirectory),
            resolvedWorldMappings
        );
        if (migration == null) {
            return null;
        }
        if (!migration.hasValues()) {
            archive(migration);
            return null;
        }
        if (!migration.unmappedPaths().isEmpty()) {
            LOGGER.warn(
                "Gale world config {} has option path(s) without Leaf mappings: {}. Deferring the whole override migration.",
                galePath,
                migration.unmappedPaths()
            );
            return null;
        }
        if (!migration.invalidPaths().isEmpty()) {
            renameOverride(galePath, "The Gale world override could not be converted.");
            return null;
        }

        boolean leafFileCreated = false;
        try {
            Files.createFile(leafPath);
            leafFileCreated = true;
            ConfigFile leafConfig = ConfigFile.loadConfig(leafFile);
            migrateValues(migration, leafConfig);
            LeafWorldConfig migrated = LeafWorldConfig.loadOverride(leafConfig, defaults);
            migrated.saveConfig();
            if (!containsAll(leafPath, migration.values().keySet())) {
                throw new IllegalStateException("Not all Gale world values were written to " + leafPath);
            }

            archive(migration);
            return migrated;
        } catch (Exception exception) {
            if (leafFileCreated) {
                try {
                    Files.deleteIfExists(leafPath);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            LOGGER.error("Failed to migrate Gale world config for {}; using Leaf world defaults.", worldDirectory, exception);
            renameOverride(galePath, "The Leaf world override could not be saved.");
            return null;
        }
    }

    private static void migrateValues(@Nullable PendingMigration migration, ConfigFile leafConfig) {
        if (migration == null) {
            return;
        }
        migration.values().forEach(leafConfig::set);
    }

    private static void complete(@Nullable PendingMigration migration, Path leafFile) {
        if (migration == null) {
            return;
        }
        List<String> remainingPaths = new ArrayList<>(migration.unmappedPaths());
        remainingPaths.addAll(migration.invalidPaths());
        if (!remainingPaths.isEmpty()) {
            LOGGER.warn("Gale config {} still has unmigrated option path(s): {}. Leaving it in place.",
                migration.sourcePath(), remainingPaths);
            return;
        }
        try {
            if (!containsAll(leafFile, migration.values().keySet())) {
                LOGGER.error("Gale values were not all written to {}; leaving {} in place.",
                    leafFile, migration.sourcePath());
                return;
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to verify migrated Leaf config {}; leaving {} in place.",
                leafFile, migration.sourcePath(), exception);
            return;
        }
        archive(migration);
    }

    private static boolean containsAll(Path leafFile, Set<String> paths) throws Exception {
        if (paths.isEmpty()) {
            return true;
        }
        ConfigFile config = ConfigFile.loadConfig(leafFile.toFile());
        return paths.stream().allMatch(config::contains);
    }

    private static Map<String, ResolvedTarget> resolveMappings(
        List<Mapping> mappings,
        boolean global
    ) {
        Map<String, ResolvedTarget> resolvedMappings = new LinkedHashMap<>();
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
            validateTarget(field, global);

            String leafPath = ConfigPathResolver.fieldPath(mapping.moduleClass(), field);

            resolvedMappings.put(oldPath, new ResolvedTarget(field, leafPath));
        }
        return Map.copyOf(resolvedMappings);
    }

    private static void validateTarget(Field field, boolean global) {
        Class<?> expectedModuleType = global ? ConfigModule.class : WorldConfigModule.class;
        if (!expectedModuleType.isAssignableFrom(field.getDeclaringClass())
            || !field.isAnnotationPresent(ConfigInfo.class)
            || Modifier.isStatic(field.getModifiers()) != global
            || Modifier.isFinal(field.getModifiers())) {
            throw new IllegalArgumentException("Invalid Gale migration target: " + field);
        }
    }

    private static Path worldContext(Path worldDirectory) {
        Path absolute = worldDirectory.toAbsolutePath().normalize();
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        return absolute.startsWith(workingDirectory)
            ? workingDirectory.relativize(absolute)
            : absolute.subpath(0, absolute.getNameCount());
    }

    private static void renameOverride(Path source, String reason) {
        Path target = uniqueOldPath(source);
        try {
            Files.move(source, target);
            LOGGER.warn("{} Renamed Gale config from {} to {}.", reason, source, target);
        } catch (IOException exception) {
            LOGGER.error("Failed to rename Gale config {}; leaving it in place.", source, exception);
        }
    }

    private static Path uniqueOldPath(Path source) {
        Path target = source.resolveSibling(source.getFileName() + "_old");
        if (!Files.exists(target)) {
            return target;
        }
        String suffix = BACKUP_TIME_FORMAT.format(LocalDateTime.now());
        for (int counter = 0; ; counter++) {
            Path candidate = source.resolveSibling(source.getFileName() + "_old-" + suffix
                + (counter == 0 ? "" : '-' + Integer.toString(counter)));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
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
        Path worldContext,
        Map<String, ResolvedTarget> mappings
    ) {
        if (!Files.isRegularFile(sourcePath)) {
            return null;
        }
        try {
            ConfigFile config = ConfigFile.loadConfig(sourcePath.toFile());
            List<String> valuePaths = new ArrayList<>();
            collectValuePaths(config, "", valuePaths);
            valuePaths.removeAll(IGNORED_PATHS);

            List<String> unmappedPaths = valuePaths.stream()
                .filter(path -> !mappings.containsKey(path))
                .toList();
            Map<String, Object> values = new LinkedHashMap<>();
            List<String> invalidPaths = new ArrayList<>();
            for (Map.Entry<String, ResolvedTarget> entry : mappings.entrySet()) {
                String oldPath = entry.getKey();
                if (!config.contains(oldPath)) {
                    continue;
                }
                Object oldValue = config.get(oldPath);
                if (oldValue == null || oldValue instanceof Map<?, ?>) {
                    LOGGER.warn("Gale path '{}' in {} is not an option; leaving it unmigrated.",
                        oldPath, sourcePath);
                    invalidPaths.add(oldPath);
                    continue;
                }
                try {
                    ResolvedTarget target = entry.getValue();
                    values.put(target.leafPath(), convertValue(oldValue, target.field()));
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Gale path '{}' in {} is invalid for {}; leaving it unmigrated.",
                        oldPath, sourcePath, entry.getValue().field(), exception);
                    invalidPaths.add(oldPath);
                }
            }

            Path backupPath = worldContext.toString().isEmpty()
                ? Path.of(sourcePath.getFileName().toString())
                : Path.of("world-overrides").resolve(worldContext).resolve(sourcePath.getFileName().toString());
            return new PendingMigration(
                sourcePath,
                backupPath,
                Map.copyOf(values),
                List.copyOf(unmappedPaths),
                List.copyOf(invalidPaths),
                !valuePaths.isEmpty()
            );
        } catch (Exception exception) {
            LOGGER.error("Failed to read Gale config {}; migration was skipped.", sourcePath, exception);
            return null;
        }
    }

    private static void archive(PendingMigration migration) {
        try {
            Path relative = migration.backupPath().normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid Gale backup path: " + relative);
            }
            Path target = backupDirectory().resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            Files.move(migration.sourcePath(), target);
            LOGGER.warn("Moved migrated Gale config {} to {}.", migration.sourcePath(), target);
        } catch (IOException exception) {
            LOGGER.error("Failed to back up Gale config {}; leaving it in place.",
                migration.sourcePath(), exception);
        }
    }

    private static void collectValuePaths(Map<?, ?> values, String parent, List<String> paths) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String path = parent.isEmpty() ? name : parent + '.' + name;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                collectValuePaths(nested, path, paths);
            } else if (value != null) {
                paths.add(path);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertValue(Object value, Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            String booleanValue = String.valueOf(value);
            if (booleanValue.equalsIgnoreCase("true")) {
                return true;
            }
            if (booleanValue.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException("Not a boolean: " + value);
        }
        if (type == int.class || type == Integer.class) {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        }
        if (type == long.class || type == Long.class) {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        }
        if (type == double.class || type == Double.class) {
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        }
        if (type == String.class) {
            return String.valueOf(value);
        }
        if (List.class.isAssignableFrom(type) && value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type, String.valueOf(value).toUpperCase(Locale.ROOT));
        }
        throw new IllegalArgumentException("Unsupported migrated config field type: " + field);
    }

    private record ResolvedTarget(Field field, String leafPath) {
    }

    private record Mapping(String oldPath, Class<?> moduleClass, String fieldName) {
    }

    private record PendingMigration(
        Path sourcePath,
        Path backupPath,
        Map<String, Object> values,
        List<String> unmappedPaths,
        List<String> invalidPaths,
        boolean hasValues
    ) {
    }
}
