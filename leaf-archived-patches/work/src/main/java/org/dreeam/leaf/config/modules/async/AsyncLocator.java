package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class AsyncLocator extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-locator";
    }

    public static boolean enabled = false;
    public static int asyncLocatorThreads = 0;
    public static int asyncLocatorKeepalive = 60;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Whether or not asynchronous locator should be enabled.
                This offloads structure locating to other threads.
                Only for locate command, dolphin treasure finding and eye of ender currently.""",
            """
                是否启用异步结构搜索.
                目前可用于 /locate 指令, 海豚寻宝和末影之眼.""");
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        asyncLocatorThreads = config.getInt(getBasePath() + ".threads", asyncLocatorThreads);
        asyncLocatorKeepalive = config.getInt(getBasePath() + ".keepalive", asyncLocatorKeepalive);

        if (asyncLocatorThreads <= 0) {
            asyncLocatorThreads = 1;
        }
        if (!enabled) {
            asyncLocatorThreads = 0;
        } else {
            LeafConfig.LOGGER.info("Using {} threads for Async Locator", asyncLocatorThreads);
        }
    }
}
