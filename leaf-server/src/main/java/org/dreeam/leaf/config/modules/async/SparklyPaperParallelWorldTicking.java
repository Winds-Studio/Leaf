package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.world.UnsafeReadPolicy;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "parallel-world-ticking", comments = {
    """
        **Experimental feature**
        Enables parallel world ticking to improve performance on multi-core systems.""",
    """
        **实验性功能**
        启用并行世界处理以提高多核 CPU 使用率."""
})
public class SparklyPaperParallelWorldTicking implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static @Experimental boolean enabled = false;

    @ConfigInfo(name = "threads")
    public static int threads = 8;

    @ConfigInfo(name = "log-container-creation-stacktraces")
    public static boolean logContainerCreationStacktraces = false;

    @ConfigInfo(name = "disable-hard-throw")
    public static boolean disableHardThrow = false;

    @ConfigInfo(name = "async-unsafe-read-handling")
    public static String asyncUnsafeReadHandling = "DISABLED";

    public static @DoNotLoad UnsafeReadPolicy asyncUnsafeReadHandlingPolicy = UnsafeReadPolicy.DISABLED;

    @Deprecated
    public static @DoNotLoad Boolean runAsyncTasksSync;

    @Override
    public void onLoaded() {
        if (enabled) {
            if (threads <= 0) threads = 8;
        } else {
            threads = 0;
        }

        logContainerCreationStacktraces = enabled && logContainerCreationStacktraces;
        disableHardThrow = enabled && disableHardThrow;

        asyncUnsafeReadHandlingPolicy = UnsafeReadPolicy.fromString(asyncUnsafeReadHandling);

        // TODO: Transfer old config
        runAsyncTasksSync = LeafConfig.globalConfig().getBoolean("async.parallel-world-ticking.run-async-tasks-sync");
        if (runAsyncTasksSync != null && runAsyncTasksSync) {
            LeafConfig.LOGGER.warn("The setting 'async.parallel-world-ticking.run-async-tasks-sync' is deprecated, removed automatically. Use 'async-unsafe-read-handling: BUFFERED' for buffered reads instead.");
        }

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Parallel World Ticking", threads);
        }
    }
}
