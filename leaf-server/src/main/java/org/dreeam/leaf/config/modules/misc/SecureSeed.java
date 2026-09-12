package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class SecureSeed extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".secure-seed";
    }

    public static boolean enabled = false;
    @Experimental
    public static boolean terrainEnabled = false;
    private static boolean secureSeedInitialized;

    public static boolean isSecureTerrainEnabled() {
        return enabled && terrainEnabled;
    }

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Once you enable secure seed, all ores and structures are generated with 1024-bit seed
                instead of using 64-bit seed in vanilla, made seed cracker become impossible.""",
            """
                安全种子开启后, 所有矿物与结构都将使用1024位的种子进行生成, 无法被破解.""");

        if (secureSeedInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        secureSeedInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        terrainEnabled = globalConfig.getBoolean(basePath() + ".terrain-enabled", terrainEnabled, globalConfig.pickStringRegionBased(
            """
                Use an independent 256-bit seed to protect terrain generation.
                """,
            """
                使用独立的 256-bit 种子来保护地形生成.""")
        );
    }
}
