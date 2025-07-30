package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

import java.util.concurrent.ThreadLocalRandom;

public class FastBiomeManagerSeedObfuscation extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".fast-biome-manager-seed-obfuscation";
    }

    public static boolean enabled = false;
    public static long seedObfuscationKey = ThreadLocalRandom.current().nextLong();
    public static String seedObfKeyPath;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled,
            config.pickStringRegionBased(
                """
                    Replace vanilla SHA-256 seed obfuscation in BiomeManager with XXHash.""",
                """
                    将原版 BiomeManager 的 SHA-256 种子混淆换成 XXHash."""));
        seedObfuscationKey = config.getLong(seedObfKeyPath = getBasePath() + ".seed-obfuscation-key", seedObfuscationKey,
            config.pickStringRegionBased(
                "Seed obfuscation key for XXHash.",
                "XXHash 的混淆种子."));
    }
}
