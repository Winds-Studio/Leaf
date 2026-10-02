package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.tracker.AsyncTracker;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

public class MultithreadedTracker extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-entity-tracker";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threads = 0;
    public static int minEntitiesPerTask = 64;
    private static boolean asyncTrackerInitialized;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                ** Experimental Feature **
                Make entity tracking asynchronously, can improve performance significantly,
                especially in some massive entities in small area situations.""", """
                ** 实验性功能 **
                异步实体跟踪,
                在实体数量多且密集的情况下效果明显.""");

        if (asyncTrackerInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        asyncTrackerInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".enabled", false);
        threads = globalConfig.getInt(basePath() + ".threads", 0);
        if (threads <= 0) {
            threads = Math.min(Runtime.getRuntime().availableProcessors() / 2, 4);
        }
        threads = Math.max(threads, 1);

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Entity Tracker", threads);
            AsyncTracker.init();
        }
    }
}
