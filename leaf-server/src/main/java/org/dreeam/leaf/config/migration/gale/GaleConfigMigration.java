package org.dreeam.leaf.config.migration.gale;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.util.ConfigFileIO;
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

        addGlobalMapping("small-optimizations.reduced-intervals.increase-time-statistics", org.dreeam.leaf.config.modules.opt.global.ReducedIntervals.class, "increaseTimeStatistics");
        addGlobalMapping("small-optimizations.reduced-intervals.update-entity-line-of-sight", org.dreeam.leaf.config.modules.opt.global.ReducedIntervals.class, "updateEntityLineOfSight");
        addGlobalMapping("gameplay-mechanics.enable-book-writing", org.dreeam.leaf.config.modules.gameplay.global.GameplayMechanics.class, "enableBookWriting");
        addGlobalMapping("misc.verify-chat-order", org.dreeam.leaf.config.modules.network.global.ChatOrderVerification.class, "enabled");
        addGlobalMapping("misc.premium-account-slow-login-timeout", org.dreeam.leaf.config.modules.network.global.PremiumAccountSlowLoginTimeout.class, "ticks");
        addGlobalMapping("misc.keepalive.send-multiple", org.dreeam.leaf.config.modules.network.global.Keepalive.class, "sendMultiple");
        addGlobalMapping("misc.last-tick-time-in-tps-command.enabled", org.dreeam.leaf.config.modules.misc.global.LastTickTimeInTpsCommand.class, "enabled");
        addGlobalMapping("misc.last-tick-time-in-tps-command.add-oversleep", org.dreeam.leaf.config.modules.misc.global.LastTickTimeInTpsCommand.class, "addOversleep");
        addGlobalMapping("log-to-console.invalid-statistics", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "invalidStatistics");
        addGlobalMapping("log-to-console.ignored-advancements", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "ignoredAdvancements");
        addGlobalMapping("log-to-console.set-block-in-far-chunk", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "setBlockInFarChunk");
        addGlobalMapping("log-to-console.unrecognized-recipes", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "unrecognizedRecipes");
        addGlobalMapping("log-to-console.legacy-material-initialization", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "legacyMaterialInitialization");
        addGlobalMapping("log-to-console.null-id-disconnections", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "nullIdDisconnections");
        addGlobalMapping("log-to-console.player-login-locations", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "playerLoginLocations");
        addGlobalMapping("log-to-console.invalid-legacy-text-component", org.dreeam.leaf.config.modules.misc.global.LogToConsole.class, "invalidLegacyTextComponent");
        addGlobalMapping("log-to-console.chat.empty-message-warning", org.dreeam.leaf.config.modules.misc.global.Chat.class, "emptyMessageWarning");
        addGlobalMapping("log-to-console.chat.expired-message-warning", org.dreeam.leaf.config.modules.misc.global.Chat.class, "expiredMessageWarning");
        addGlobalMapping("log-to-console.chat.not-secure-marker", org.dreeam.leaf.config.modules.misc.global.Chat.class, "notSecureMarker");
        addGlobalMapping("log-to-console.plugin-library-loader.downloads", org.dreeam.leaf.config.modules.misc.global.PluginLibraryLoader.class, "downloads");
        addGlobalMapping("log-to-console.plugin-library-loader.start-load-libraries-for-plugin", org.dreeam.leaf.config.modules.misc.global.PluginLibraryLoader.class, "startLoadLibrariesForPlugin");
        addGlobalMapping("log-to-console.plugin-library-loader.library-loaded", org.dreeam.leaf.config.modules.misc.global.PluginLibraryLoader.class, "libraryLoaded");

        addWorldMapping("small-optimizations.save-fireworks", org.dreeam.leaf.config.modules.opt.world.SaveFireworks.class, "enabled");
        addWorldMapping("small-optimizations.use-optimized-sheep-offspring-color", org.dreeam.leaf.config.modules.opt.world.OptimizedSheepOffspringColor.class, "enabled");
        addWorldMapping("small-optimizations.max-projectile-chunk-loads.per-tick", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perTick");
        addWorldMapping("small-optimizations.max-projectile-chunk-loads.per-projectile.max", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileMax");
        addWorldMapping("small-optimizations.max-projectile-chunk-loads.per-projectile.reset-movement-after-reach-limit", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileResetMovementAfterReachLimit");
        addWorldMapping("small-optimizations.max-projectile-chunk-loads.per-projectile.remove-from-world-after-reach-limit", org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads.class, "perProjectileRemoveFromWorldAfterReachLimit");
        addWorldMapping("small-optimizations.reduced-intervals.check-stuck-in-wall", org.dreeam.leaf.config.modules.opt.world.ReducedIntervals.class, "checkStuckInWall");
        addWorldMapping("small-optimizations.reduced-intervals.villager-item-repickup", org.dreeam.leaf.config.modules.opt.world.ReducedIntervals.class, "villagerItemRepickup");
        addWorldMapping("small-optimizations.load-chunks.to-spawn-phantoms", org.dreeam.leaf.config.modules.opt.world.LoadChunks.class, "toSpawnPhantoms");
        addWorldMapping("small-optimizations.load-chunks.to-activate-climbing-entities", org.dreeam.leaf.config.modules.opt.world.LoadChunks.class, "toActivateClimbingEntities");
        addWorldMapping("gameplay-mechanics.fixes.broadcast-crit-animations-as-the-entity-being-critted", org.dreeam.leaf.config.modules.fixes.world.Fixes.class, "broadcastCritAnimationsAsTheEntityBeingCritted");
        addWorldMapping("gameplay-mechanics.fixes.mc-238526", org.dreeam.leaf.config.modules.fixes.world.Fixes.class, "mc238526");
        addWorldMapping("gameplay-mechanics.fixes.mc-121706", org.dreeam.leaf.config.modules.fixes.world.Fixes.class, "mc121706");
        addWorldMapping("gameplay-mechanics.entities-can-random-stroll-into-non-ticking-chunks", org.dreeam.leaf.config.modules.gameplay.world.RandomStrollIntoNonTickingChunks.class, "enabled");
        addWorldMapping("gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation", org.dreeam.leaf.config.modules.opt.world.EntityWakeUpDuration.class, "ratioStandardDeviation");
        addWorldMapping("gameplay-mechanics.hide-flames-on-entities-with-fire-resistance", org.dreeam.leaf.config.modules.gameplay.world.HideFlamesOnEntitiesWithFireResistance.class, "enabled");
        addWorldMapping("gameplay-mechanics.try-respawn-ender-dragon-after-end-crystal-place", org.dreeam.leaf.config.modules.gameplay.world.EnderDragonRespawn.class, "tryAfterEndCrystalPlace");
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

        boolean galeConfigFound = archive(configDirectory.resolve(GLOBAL_FILE), Path.of(GLOBAL_FILE));
        galeConfigFound |= archive(configDirectory.resolve(WORLD_DEFAULTS_FILE), Path.of(WORLD_DEFAULTS_FILE));
        for (ServerLevel level : server.getAllLevels()) {
            Path worldDirectory = server.storageSource.getDimensionPath(level.dimension());
            Path absoluteWorldDirectory = worldDirectory.toAbsolutePath().normalize();
            Path workingDirectory = Path.of("").toAbsolutePath().normalize();
            Path worldContext = absoluteWorldDirectory.startsWith(workingDirectory)
                ? workingDirectory.relativize(absoluteWorldDirectory)
                : absoluteWorldDirectory.subpath(0, absoluteWorldDirectory.getNameCount());
            Path backupPath = Path.of("world-overrides").resolve(worldContext).resolve(WORLD_OVERRIDE_FILE);
            galeConfigFound |= archive(worldDirectory.resolve(WORLD_OVERRIDE_FILE), backupPath);
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
            if (galeConfig == null) {
                return null;
            }
            boolean hasConfigValues = false;
            for (String path : galeConfig.getKeys(false, true)) {
                if (!IGNORED_PATHS.contains(path)) {
                    hasConfigValues = true;
                    break;
                }
            }
            if (!hasConfigValues) {
                return null;
            }

            Files.createFile(leafPath);
            leafFileCreated = true;
            ConfigFile leafConfig = ConfigFileIO.load(leafFile);
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
        try {
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
        } catch (IOException exception) {
            LOGGER.error("Failed to back up Gale config {}; leaving it in place.", srcPath, exception);
        }
        return true;
    }

    private record Mapping(String oldPath, Class<?> moduleClass, String fieldName) {
    }
}
