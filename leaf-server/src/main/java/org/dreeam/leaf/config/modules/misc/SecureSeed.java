package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SecureSeed extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".secure-seed";
    }

    public static boolean enabled = false;
    public static int type = 2;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Once you enable secure seed, all ores and structures are generated with 1024-bit seed
                instead of using 64-bit seed in vanilla, made seed cracker become impossible.""",
            """
                安全种子开启后, 所有矿物与结构都将使用1024位的种子进行生成, 无法被破解.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        int loadedType = config.getInt(getBasePath() + ".type", type, config.pickStringRegionBased(
            "Type of hashing: Blake2b - 2, Blake3 - 3",
            "哈希的类型：blake2b - 2，blake3 - 3"
        ));
        if (loadedType != 2 && loadedType != 3) {
            type = 2;
        } else {
            type = loadedType;
        }
    }
}
