package org.dreeam.leaf.config.migration.gale;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.migration.ConfigBackupSession;
import org.dreeam.leaf.config.modules.fixes.world.Fixes;
import org.dreeam.leaf.config.modules.gameplay.BookWriting;
import org.dreeam.leaf.config.modules.network.ChatOrderVerification;
import org.dreeam.leaf.config.modules.network.Keepalive;
import org.dreeam.leaf.config.modules.network.PremiumAccountSlowLoginTimeout;
import org.dreeam.leaf.config.util.ConfigFileIO;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import org.dreeam.leaf.config.util.ConfigPaths;
import org.dreeam.leaf.config.util.ConfigValues;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
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
    private static List<ResolvedMapping> worldMappings = List.of();
    private static ConfigBackupSession backupSession;
    private static final Set<Path> RETAINED_GALE_SOURCES = new HashSet<>();

    /**
     * Imports the Gale global configuration and world defaults into the raw Leaf files.
     *
     * @throws IllegalArgumentException if a value that has to be imported is not valid for its option
     */
    public static void migrate(
        Path directory,
        ConfigFile leafGlobalConfig,
        ConfigFile leafWorldDefaultsConfig,
        ConfigBackupSession session
    ) {
        configDirectory = Objects.requireNonNull(directory, "directory").normalize();
        backupSession = Objects.requireNonNull(session, "session");
        RETAINED_GALE_SOURCES.clear();

        Path globalPath = configDirectory.resolve(GLOBAL_FILE);
        Path worldDefaultsPath = configDirectory.resolve(WORLD_DEFAULTS_FILE);

        List<ResolvedMapping> globalMappings = resolveMappings(registerGlobalMappings());
        worldMappings = resolveMappings(registerWorldMappings());
        migrateStage(globalPath, globalMappings, leafGlobalConfig);
        migrateStage(worldDefaultsPath, worldMappings, leafWorldDefaultsConfig);
    }

    static List<Mapping> registerGlobalMappings() {
        List<Mapping> globalMappings = new ArrayList<>();

        globalMappings.add(new Mapping("small-optimizations.reduced-intervals.increase-time-statistics", org.dreeam.leaf.config.modules.opt.ReducedIntervals.class, "increaseTimeStatistics"));
        globalMappings.add(new Mapping("small-optimizations.reduced-intervals.update-entity-line-of-sight", org.dreeam.leaf.config.modules.opt.ReducedIntervals.class, "updateEntityLineOfSight"));
        globalMappings.add(new Mapping("gameplay-mechanics.enable-book-writing", BookWriting.class, "enabled"));
        globalMappings.add(new Mapping("misc.verify-chat-order", ChatOrderVerification.class, "enabled"));
        globalMappings.add(new Mapping("misc.premium-account-slow-login-timeout", PremiumAccountSlowLoginTimeout.class, "ticks"));
        globalMappings.add(new Mapping("misc.keepalive.send-multiple", Keepalive.class, "sendMultiple"));
        globalMappings.add(new Mapping("misc.last-tick-time-in-tps-command.enabled", org.dreeam.leaf.config.modules.misc.LastTickTimeInTpsCommand.class, "enabled"));
        globalMappings.add(new Mapping("misc.last-tick-time-in-tps-command.add-oversleep", org.dreeam.leaf.config.modules.misc.LastTickTimeInTpsCommand.class, "addOversleep"));
        globalMappings.add(new Mapping("log-to-console.invalid-statistics", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "invalidStatistics"));
        globalMappings.add(new Mapping("log-to-console.ignored-advancements", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "ignoredAdvancements"));
        globalMappings.add(new Mapping("log-to-console.set-block-in-far-chunk", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "setBlockInFarChunk"));
        globalMappings.add(new Mapping("log-to-console.unrecognized-recipes", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "unrecognizedRecipes"));
        globalMappings.add(new Mapping("log-to-console.legacy-material-initialization", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "legacyMaterialInitialization"));
        globalMappings.add(new Mapping("log-to-console.null-id-disconnections", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "nullIdDisconnections"));
        globalMappings.add(new Mapping("log-to-console.player-login-locations", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "playerLoginLocations"));
        globalMappings.add(new Mapping("log-to-console.invalid-legacy-text-component", org.dreeam.leaf.config.modules.misc.LogToConsole.class, "invalidLegacyTextComponent"));
        globalMappings.add(new Mapping("log-to-console.chat.empty-message-warning", org.dreeam.leaf.config.modules.misc.Chat.class, "emptyMessageWarning"));
        globalMappings.add(new Mapping("log-to-console.chat.expired-message-warning", org.dreeam.leaf.config.modules.misc.Chat.class, "expiredMessageWarning"));
        globalMappings.add(new Mapping("log-to-console.chat.not-secure-marker", org.dreeam.leaf.config.modules.misc.Chat.class, "notSecureMarker"));
        globalMappings.add(new Mapping("log-to-console.plugin-library-loader.downloads", org.dreeam.leaf.config.modules.misc.PluginLibraryLoader.class, "downloads"));
        globalMappings.add(new Mapping("log-to-console.plugin-library-loader.start-load-libraries-for-plugin", org.dreeam.leaf.config.modules.misc.PluginLibraryLoader.class, "startLoadLibrariesForPlugin"));
        globalMappings.add(new Mapping("log-to-console.plugin-library-loader.library-loaded", org.dreeam.leaf.config.modules.misc.PluginLibraryLoader.class, "libraryLoaded"));
        return globalMappings;
    }

    static List<Mapping> registerWorldMappings() {
        List<Mapping> worldMappings = new ArrayList<>();
        worldMappings.add(new Mapping("small-optimizations.save-fireworks", org.dreeam.leaf.config.modules.opt.world.SaveFireworks.class, "enabled"));
        worldMappings.add(new Mapping("small-optimizations.use-optimized-sheep-offspring-color", org.dreeam.leaf.config.modules.opt.world.OptimizedSheepOffspringColor.class, "enabled"));
        worldMappings.add(new Mapping("small-optimizations.max-projectile-chunk-loads.per-tick", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perTick"));
        worldMappings.add(new Mapping("small-optimizations.max-projectile-chunk-loads.per-projectile.max", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileMax"));
        worldMappings.add(new Mapping("small-optimizations.max-projectile-chunk-loads.per-projectile.reset-movement-after-reach-limit", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileResetMovementAfterReachLimit"));
        worldMappings.add(new Mapping("small-optimizations.max-projectile-chunk-loads.per-projectile.remove-from-world-after-reach-limit", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileRemoveFromWorldAfterReachLimit"));
        worldMappings.add(new Mapping("small-optimizations.reduced-intervals.check-stuck-in-wall", org.dreeam.leaf.config.modules.opt.world.ReducedIntervals.class, "checkStuckInWall"));
        worldMappings.add(new Mapping("small-optimizations.reduced-intervals.villager-item-repickup", org.dreeam.leaf.config.modules.opt.world.ReducedIntervals.class, "villagerItemRepickup"));
        worldMappings.add(new Mapping("small-optimizations.load-chunks.to-spawn-phantoms", org.dreeam.leaf.config.modules.opt.world.LoadChunks.class, "toSpawnPhantoms"));
        worldMappings.add(new Mapping("small-optimizations.load-chunks.to-activate-climbing-entities", org.dreeam.leaf.config.modules.opt.world.LoadChunks.class, "toActivateClimbingEntities"));
        worldMappings.add(new Mapping("gameplay-mechanics.fixes.broadcast-crit-animations-as-the-entity-being-critted", Fixes.class, "broadcastCritAnimationsAsTheEntityBeingCritted"));
        worldMappings.add(new Mapping("gameplay-mechanics.fixes.mc-238526", Fixes.class, "mc238526"));
        worldMappings.add(new Mapping("gameplay-mechanics.fixes.mc-121706", Fixes.class, "mc121706"));
        worldMappings.add(new Mapping("gameplay-mechanics.entities-can-random-stroll-into-non-ticking-chunks", org.dreeam.leaf.config.modules.gameplay.world.RandomStrollIntoNonTickingChunks.class, "enabled"));
        worldMappings.add(new Mapping("gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation", org.dreeam.leaf.config.modules.opt.world.EntityWakeUpDuration.class, "ratioStandardDeviation"));
        worldMappings.add(new Mapping("gameplay-mechanics.hide-flames-on-entities-with-fire-resistance", org.dreeam.leaf.config.modules.gameplay.world.HideFlamesOnEntitiesWithFireResistance.class, "enabled"));
        worldMappings.add(new Mapping("gameplay-mechanics.try-respawn-ender-dragon-after-end-crystal-place", org.dreeam.leaf.config.modules.gameplay.world.EnderDragonRespawn.class, "tryAfterEndCrystalPlace"));
        return worldMappings;
    }

    /**
     * Archives the Gale global configuration and world defaults once the Leaf files were saved. A source whose
     * Leaf file could not be saved stays where it is and is converted again at the next start.
     *
     * @param report the result of saving the Leaf global configuration and world defaults
     */
    public static void archiveGlobalSources(ConfigFileIO.SaveReport report) {
        if (configDirectory == null) {
            return;
        }
        boolean archived = archiveIfSaved(report, "leaf-global.yml", GLOBAL_FILE);
        archived |= archiveIfSaved(report, "leaf-world-defaults.yml", WORLD_DEFAULTS_FILE);
        if (archived) {
            warnMigrated();
        }
    }

    private static boolean archiveIfSaved(ConfigFileIO.SaveReport report, String leafFile, String galeFile) {
        Path leafPath = configDirectory.resolve(leafFile).toAbsolutePath().normalize();
        for (ConfigFileIO.SaveResult result : report.results()) {
            if (result.target().equals(leafPath) && result.status() != ConfigFileIO.SaveStatus.SAVED) {
                RETAINED_GALE_SOURCES.add(configDirectory.resolve(galeFile).toAbsolutePath().normalize());
            }
        }
        return archive(configDirectory.resolve(galeFile), Path.of(galeFile));
    }

    /**
     * Archives the Gale world configuration of a world that was loaded, after its Leaf configuration was
     * loaded and, if it had to be created, saved. Runs for every world when it is loaded, including worlds that
     * plugins create later.
     */
    public static void archiveWorldSource(Path worldDirectory) {
        if (configDirectory == null) {
            return;
        }
        Path absoluteWorldDirectory = worldDirectory.toAbsolutePath().normalize();
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path worldContext = absoluteWorldDirectory.startsWith(workingDirectory)
            ? workingDirectory.relativize(absoluteWorldDirectory)
            : absoluteWorldDirectory.subpath(0, absoluteWorldDirectory.getNameCount());
        Path backupPath = Path.of("world-overrides").resolve(worldContext).resolve(WORLD_OVERRIDE_FILE);
        if (archive(worldDirectory.resolve(WORLD_OVERRIDE_FILE), backupPath)) {
            warnMigrated();
        }
    }

    private static void warnMigrated() {
        LOGGER.warn(
            "Gale configuration migration has finished. Please manually check the migrated Leaf configuration files for correctness."
        );
    }

    /**
     * Attempts to create a Leaf override from Gale values.
     *
     * @return the newly created Leaf override, or {@code null} when normal Leaf loading should continue
     * @throws IllegalArgumentException if a value that has to be imported is not valid for its option;
     *                                  the world is not loaded and the Gale file is kept
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

        if (leafFile.isFile()) {
            LOGGER.warn(
                "Could not migrate Gale world config {} because Leaf world override {} already exists.",
                galePath, leafPath
            );
            return null;
        }

        ConfigFile galeConfig = loadSrcConfig(galePath);
        if (galeConfig == null) {
            return null;
        }
        warnUnmapped(galePath, galeConfig, worldMappings);
        Map<String, Object> values = collectValues(galePath, galeConfig, worldMappings, null);
        if (values.isEmpty()) {
            // Nothing to override: the world uses the defaults, and no file is created for it.
            return null;
        }

        try {
            ConfigFile leafConfig = ConfigFileIO.load(leafFile);
            values.forEach(leafConfig::set);
            leafConfig.set(LeafConfig.VERSION_PATH, LeafConfig.CURRENT_CONFIG_VERSION);
            LeafWorldConfig migrated = LeafConfig.loadWorldOverride(leafConfig, defaults);
            ConfigFileIO.SaveReport report = ConfigFileIO.save(leafConfig);
            if (report.hasAccessDenied()) {
                RETAINED_GALE_SOURCES.add(galePath.toAbsolutePath().normalize());
            }
            return migrated;
        } catch (Exception exception) {
            LOGGER.error(
                "Failed to migrate Gale world config {} for {}; using Leaf world defaults.",
                galePath, worldDirectory, exception
            );
            RETAINED_GALE_SOURCES.add(galePath.toAbsolutePath().normalize());
            return null;
        }
    }

    /**
     * Imports the values of one Gale file into the Leaf file it moved to: values the Leaf file does not
     * have yet, once, without touching the file when a value is rejected.
     */
    private static void migrateStage(Path srcPath, List<ResolvedMapping> mappings, ConfigFile leafConfig) {
        ConfigFile srcConfig = loadSrcConfig(srcPath);
        if (srcConfig == null) {
            return;
        }
        warnUnmapped(srcPath, srcConfig, mappings);
        collectValues(srcPath, srcConfig, mappings, leafConfig).forEach(leafConfig::set);
    }

    /**
     * Reads the values that are to be imported from a Gale file, converted to the type of their option.
     *
     * <p>A null value is a missing value, as Gale reads it. A value that Gale would not have accepted for
     * the option is an error; nothing is imported then. With {@code leafConfig}, an option that already has a
     * value in the Leaf file keeps it, and a different Gale value is only warned about.</p>
     *
     * @throws IllegalArgumentException if a value is not valid for its option
     */
    private static Map<String, Object> collectValues(
        Path srcPath,
        ConfigFile srcConfig,
        List<ResolvedMapping> mappings,
        @Nullable ConfigFile leafConfig
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ResolvedMapping mapping : mappings) {
            Object raw = srcConfig.get(mapping.oldPath());
            if (raw == null) {
                continue;
            }
            Object value = readValue(mapping.type(), srcConfig, mapping.oldPath(), raw);
            Object existing = leafConfig == null ? null : leafConfig.get(mapping.leafPath());
            if (existing == null) {
                values.put(mapping.leafPath(), value);
            } else if (!String.valueOf(existing).equals(String.valueOf(value))) {
                LOGGER.warn("Keeping the Leaf value {} of {} and ignoring the value {} of {} in {}.",
                    existing, mapping.leafPath(), value, mapping.oldPath(), srcPath);
            }
        }
        return values;
    }

    /**
     * Reads a value the way Gale's configuration did (Configurate 4.2.0 scalar serializers): a boolean also
     * accepts {@code t}, {@code yes}, {@code y}, {@code 1}, {@code f}, {@code no}, {@code n}, {@code 0} and
     * numbers, a number with an integral value is a valid integer.
     */
    private static Object readValue(Class<?> type, ConfigFile srcConfig, String path, Object raw) {
        if (raw instanceof List<?> || raw instanceof Map<?, ?> || raw instanceof ConfigSection) {
            throw ConfigValues.invalid(srcConfig, path, type.getSimpleName(), raw, null);
        }
        if (type == boolean.class || type == Boolean.class) {
            if (raw instanceof Number) {
                return !raw.equals(0);
            }
            return switch (String.valueOf(raw).toLowerCase(Locale.ROOT)) {
                case "true", "t", "yes", "y", "1" -> Boolean.TRUE;
                case "false", "f", "no", "n", "0" -> Boolean.FALSE;
                default -> throw ConfigValues.invalid(srcConfig, path, "boolean", raw, null);
            };
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            boolean isInt = type == int.class || type == Integer.class;
            if (raw instanceof Float || raw instanceof Double) {
                double value = ((Number) raw).doubleValue();
                double absolute = Math.abs(value);
                if (absolute - Math.floor(absolute) < 1.0E-8D && absolute <= (isInt ? Integer.MAX_VALUE : Long.MAX_VALUE)) {
                    return isInt ? (Object) (int) value : (Object) (long) value;
                }
                throw ConfigValues.invalid(srcConfig, path, isInt ? "int" : "long", raw, null);
            }
            if (raw instanceof Number number) {
                long value = number.longValue();
                if (isInt && (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)) {
                    throw ConfigValues.invalid(srcConfig, path, "int", raw, null);
                }
                return isInt ? (Object) (int) value : (Object) value;
            }
        }
        // Everything else is read like a Leaf option: numbers from their text, strings as they are.
        return ConfigValues.parse(type, srcConfig, path, raw);
    }

    /** Warns about the options of a Gale file that Leaf has no equivalent for. */
    private static void warnUnmapped(Path srcPath, ConfigFile srcConfig, List<ResolvedMapping> mappings) {
        Set<String> mapped = new HashSet<>();
        for (ResolvedMapping mapping : mappings) {
            mapped.add(mapping.oldPath());
        }
        List<String> unmapped = new ArrayList<>();
        collectPaths("", srcConfig, unmapped);
        unmapped.removeIf(path -> mapped.contains(path) || IGNORED_PATHS.contains(path));
        if (!unmapped.isEmpty()) {
            LOGGER.warn("Gale config {} has options that Leaf does not support anymore; they are not migrated: {}", srcPath, unmapped);
        }
    }

    private static void collectPaths(String prefix, ConfigSection section, List<String> paths) {
        for (String key : section.getKeys(false, true)) {
            Object value = section.get(key);
            if (value instanceof ConfigSection child) {
                collectPaths(prefix + key + '.', child, paths);
            } else {
                paths.add(prefix + key);
            }
        }
    }

    /** Resolves the Leaf path and type of every target; a target that does not exist is a defect of the list. */
    private static List<ResolvedMapping> resolveMappings(List<Mapping> mappings) {
        List<ResolvedMapping> resolvedMappings = new ArrayList<>(mappings.size());
        for (Mapping mapping : mappings) {
            Field field;
            try {
                field = mapping.moduleClass().getDeclaredField(mapping.fieldName());
            } catch (NoSuchFieldException exception) {
                throw new IllegalArgumentException("Invalid Gale migration target "
                    + mapping.moduleClass().getName() + '.' + mapping.fieldName(), exception);
            }
            resolvedMappings.add(new ResolvedMapping(
                mapping.oldPath(), ConfigPaths.fieldPath(mapping.moduleClass(), field), field.getType()
            ));
        }
        return resolvedMappings;
    }

    private static @Nullable ConfigFile loadSrcConfig(Path srcPath) {
        if (!Files.isRegularFile(srcPath)) {
            return null;
        }
        try {
            return ConfigFileIO.load(srcPath.toFile());
        } catch (Exception exception) {
            LOGGER.error("Failed to read Gale config {}; migration was skipped.", srcPath, exception);
            return null;
        }
    }

    private static boolean archive(Path srcPath, Path backupPath) {
        if (!Files.isRegularFile(srcPath)) return false;
        if (RETAINED_GALE_SOURCES.contains(srcPath.toAbsolutePath().normalize())) {
            LOGGER.error("Retaining Gale config {} because its migrated Leaf file was not saved.", srcPath);
            return true;
        }
        try {
            Path relative = backupPath.normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid Gale backup path: " + relative);
            }
            backupSession.move(srcPath, relative);
            LOGGER.warn("Moved Gale config {} to {}.", srcPath, backupSession.directory().resolve(relative));
        } catch (IOException exception) {
            LOGGER.error("Failed to back up Gale config {}; leaving it in place.", srcPath, exception);
        }
        return true;
    }

    record Mapping(String oldPath, Class<?> moduleClass, String fieldName) {
    }

    private record ResolvedMapping(String oldPath, String leafPath, Class<?> type) {
    }
}
