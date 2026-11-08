package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class EnableCachedMTBEntityTypeConvert implements ConfigModule {

    @ConfigInfo(name = "enable-cached-minecraft-to-bukkit-entitytype-convert", comments = {
        "Whether to cache expensive CraftEntityType#minecraftToBukkit call.",
        "是否缓存Minecraft到Bukkit的实体类型转换."
    })
    public static boolean enabled = true;
}
