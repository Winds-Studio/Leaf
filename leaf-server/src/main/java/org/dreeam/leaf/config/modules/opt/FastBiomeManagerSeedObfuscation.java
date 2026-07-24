package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

import java.util.concurrent.ThreadLocalRandom;

public class FastBiomeManagerSeedObfuscation extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".fast-biome-manager-seed-obfuscation";
    }

    public static boolean enabled = false;
    public static long seedObfuscationKey = ThreadLocalRandom.current().nextLong();
    public static String seedObfKeyPath;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled,
            globalConfig.pickStringRegionBased(
                """
                    Replace vanilla SHA-256 seed obfuscation in BiomeManager with XXHash.""",
                """
                    将原版 BiomeManager 的 SHA-256 种子混淆换成 XXHash."""));
        seedObfuscationKey = globalConfig.getLong(seedObfKeyPath = basePath() + ".seed-obfuscation-key", seedObfuscationKey,
            globalConfig.pickStringRegionBased(
                "Seed obfuscation key for XXHash.",
                "XXHash 的混淆种子."));
    }
}
