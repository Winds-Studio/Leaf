package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.tracker.AsyncTracker;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "async-entity-tracker", comments = {
    """
        ** Experimental Feature **
        Make entity tracking asynchronously, can improve performance significantly,
        especially in some massive entities in small area situations.""",
    """
        ** 实验性功能 **
        异步实体跟踪,
        在实体数量多且密集的情况下效果明显."""
})
public class MultithreadedTracker implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static @Experimental boolean enabled = false;

    @ConfigInfo(name = "threads")
    public static int threads = 0;

    @Override
    public void onLoaded() {
        if (threads <= 0) {
            threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        }
        threads = Math.max(threads, 1);

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Entity Tracker", threads);
            AsyncTracker.init();
        }
    }
}
