package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class PracticeSpawnControl extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".practice-spawn-control";
    }

    public static boolean enabled = true;
    public static boolean naturalMobSpawning = false;
    public static boolean allowPluginSpawns = true;
    public static boolean preventSpawnIntoUnloadedChunks = true;

    public static boolean oakBoatLimiter = true;
    public static int oakBoatMaxPerChunk = 16;

    public static boolean tntMinecartLimiter = true;
    public static int tntMinecartMaxPerChunk = 8;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        naturalMobSpawning = config.getBoolean(getBasePath() + ".natural-mob-spawning", naturalMobSpawning);
        allowPluginSpawns = config.getBoolean(getBasePath() + ".allow-plugin-spawns", allowPluginSpawns);
        preventSpawnIntoUnloadedChunks = config.getBoolean(getBasePath() + ".prevent-spawn-into-unloaded-chunks", preventSpawnIntoUnloadedChunks);

        oakBoatLimiter = config.getBoolean(getBasePath() + ".entity-limiter.oak-boat.enabled", oakBoatLimiter);
        oakBoatMaxPerChunk = Math.max(0, config.getInt(getBasePath() + ".entity-limiter.oak-boat.max-per-chunk", oakBoatMaxPerChunk));

        tntMinecartLimiter = config.getBoolean(getBasePath() + ".entity-limiter.tnt-minecart.enabled", tntMinecartLimiter);
        tntMinecartMaxPerChunk = Math.max(0, config.getInt(getBasePath() + ".entity-limiter.tnt-minecart.max-per-chunk", tntMinecartMaxPerChunk));
    }
}
