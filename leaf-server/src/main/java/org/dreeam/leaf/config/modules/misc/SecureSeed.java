package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "secure-seed", comments = {
    """
        Once you enable secure seed, all ores and structures are generated with 1024-bit seed
        instead of using 64-bit seed in vanilla, made seed cracker become impossible.""",
    """
        安全种子开启后, 所有矿物与结构都将使用1024位的种子进行生成, 无法被破解."""
})
public class SecureSeed implements ConfigModule {

    @HotReloadUnsupported
    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;
}
