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

    public static boolean oakBoatLimiter = true;
    public static int oakBoatMaxPerWorld = 256;

    public static boolean tntMinecartLimiter = true;
    public static int tntMinecartMaxPerWorld = 128;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        naturalMobSpawning = config.getBoolean(getBasePath() + ".natural-mob-spawning", naturalMobSpawning);
        allowPluginSpawns = config.getBoolean(getBasePath() + ".allow-plugin-spawns", allowPluginSpawns);

        oakBoatLimiter = config.getBoolean(getBasePath() + ".entity-limiter.oak-boat.enabled", oakBoatLimiter);
        oakBoatMaxPerWorld = Math.max(0, config.getInt(getBasePath() + ".entity-limiter.oak-boat.max-per-world", oakBoatMaxPerWorld));

        tntMinecartLimiter = config.getBoolean(getBasePath() + ".entity-limiter.tnt-minecart.enabled", tntMinecartLimiter);
        tntMinecartMaxPerWorld = Math.max(0, config.getInt(getBasePath() + ".entity-limiter.tnt-minecart.max-per-world", tntMinecartMaxPerWorld));
    }
}
