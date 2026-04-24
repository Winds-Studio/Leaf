package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class SecureSeed extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".secure-seed";
    }

    public static boolean enabled = false;
    public static boolean useBlake3 = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Once you enable secure seed, all ores and structures are generated with 1024-bit seed
                instead of using 64-bit seed in vanilla, made seed cracker become impossible.""",
            """
                安全种子开启后, 所有矿物与结构都将使用1024位的种子进行生成, 无法被破解.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);

        String algorithm = config.getString(getBasePath() + ".hash-algorithm", "blake2b",
            config.pickStringRegionBased(
                """
                    Hash algorithm used for secure seed generation.
                    Accepted values: blake2b, blake3
                    blake3 uses fewer rounds (4 vs 12) and ThreadLocal state pooling, which may be faster.""",
                """
                    用于安全种子生成的哈希算法.
                    可选值: blake2b, blake3
                    blake3 使用更少的轮次 (4 vs 12) 并使用 ThreadLocal 状态池, 可能更快."""));
        if (algorithm.equalsIgnoreCase("blake3")) {
            useBlake3 = true;
        } else {
            if (!algorithm.equalsIgnoreCase("blake2b")) {
                LeafConfig.LOGGER.warn("Unknown hash-algorithm '{}' for secure-seed, falling back to blake2b.", algorithm);
            }
            useBlake3 = false;
        }
    }
}
