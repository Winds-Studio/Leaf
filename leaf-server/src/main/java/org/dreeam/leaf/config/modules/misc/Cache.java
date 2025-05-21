package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class Cache extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".cache";
    }

    public static boolean cachePlayerProfileResult = false;
    public static int cachePlayerProfileResultTimeout = 1440;

    @Override
    public void onLoaded() {
        cachePlayerProfileResult = config.getBoolean(getBasePath() + ".cache-player-profile-result", cachePlayerProfileResult, config.pickStringRegionBased("""
                Cache the player profile result on they first join.
                It's useful if Mojang's verification server is down.""",
            """
                玩家首次加入时缓存 PlayerProfile.
                正版验证服务器宕机时非常有用."""));
        cachePlayerProfileResultTimeout = config.getInt(getBasePath() + ".cache-player-profile-result-timeout", cachePlayerProfileResultTimeout,
            config.pickStringRegionBased(
                "The timeout of the cache. Unit: Minutes.",
                "缓存过期时间. 单位: 分钟."
            ));
    }
}
