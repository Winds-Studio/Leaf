package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class EnableCachedMTBEntityTypeConvert extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enable-cached-minecraft-to-bukkit-entitytype-convert", enabled, globalConfig.pickStringRegionBased("""
                Whether to cache expensive CraftEntityType#minecraftToBukkit call.""",
            """
                是否缓存Minecraft到Bukkit的实体类型转换."""));
    }
}
