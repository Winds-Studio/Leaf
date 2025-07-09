package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.tracker.AsyncTracker;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

public class MultithreadedTracker extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-entity-tracker";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threads = 0;
    public static boolean noBlocking = true;
    private static boolean asyncMultithreadedTrackerInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                ** Experimental Feature **
                Make entity tracking asynchronously, can improve performance significantly,
                especially in some massive entities in small area situations.""", """
                ** 实验性功能 **
                异步实体跟踪,
                在实体数量多且密集的情况下效果明显.""");

        if (asyncMultithreadedTrackerInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncMultithreadedTrackerInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".force-enabled", false);
        boolean old = config.getBoolean(getBasePath() + ".enabled", false);
        if (old && !enabled) {
            LOGGER.warn("Disabled async-entity-tracker due to experimentation. Set force-enabled to true to enable.");
        }

        threads = config.getInt(getBasePath() + ".threads", 0);
        if (threads <= 0) {
            threads = 1;
        }
        noBlocking = config.getBoolean(getBasePath() + ".no-blocking", noBlocking);
        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Entity Tracker", threads);
            AsyncTracker.init(threads);
        }
    }
}
