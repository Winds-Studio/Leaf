package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class CacheProfileLookup extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".cache.profile-lookup";
    }

    public static boolean enabled = false;
    public static int timeout = 1440; // 24 hours in minutes
    public static int maxSize = 8192;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled, config.pickStringRegionBased("""
                Cache profile data lookups (skins, textures, etc.) to reduce API calls to Mojang.""",
            """
                缓存玩家资料查询 (皮肤, 材质等) 以减少对 Mojang API 的调用."""));
        timeout = config.getInt(getBasePath() + ".timeout", timeout, config.pickStringRegionBased(
                "The timeout for profile lookup cache. Unit: Minutes.",
                "玩家资料查询缓存过期时间. 单位: 分钟. (推荐: 1440 = 24小时)"
            ));
        maxSize = config.getInt(getBasePath() + ".max-size", maxSize, config.pickStringRegionBased(
                "Maximum number of profiles to cache. Higher values use more memory (not that much).",
                "最大缓存的玩家资料数量. 更高的值会使用更多内存."
            ));
    }
}
