package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.world.UnsafeReadPolicy;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

public class SparklyPaperParallelWorldTicking extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".parallel-world-ticking";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threads = 8;
    public static boolean logContainerCreationStacktraces = false;
    public static boolean disableHardThrow = false;
    @Deprecated
    public static Boolean runAsyncTasksSync;
    public static UnsafeReadPolicy asyncUnsafeReadHandling = UnsafeReadPolicy.DISABLED;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                **Experimental feature**
                Enables parallel world ticking to improve performance on multi-core systems.""",
            """
                **实验性功能**
                启用并行世界处理以提高多核 CPU 使用率.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        threads = globalConfig.getInt(basePath() + ".threads", threads);
        if (enabled) {
            if (threads <= 0) threads = 8;
        } else {
            threads = 0;
        }

        logContainerCreationStacktraces = globalConfig.getBoolean(basePath() + ".log-container-creation-stacktraces", logContainerCreationStacktraces);
        logContainerCreationStacktraces = enabled && logContainerCreationStacktraces;
        disableHardThrow = globalConfig.getBoolean(basePath() + ".disable-hard-throw", disableHardThrow);
        disableHardThrow = enabled && disableHardThrow;
        asyncUnsafeReadHandling = UnsafeReadPolicy.fromString(globalConfig.getString(basePath() + ".async-unsafe-read-handling", asyncUnsafeReadHandling.toString()));

        // Transfer old config
        runAsyncTasksSync = globalConfig.getBoolean(basePath() + ".run-async-tasks-sync");
        if (runAsyncTasksSync != null && runAsyncTasksSync) {
            LeafConfig.LOGGER.warn("The setting '{}.run-async-tasks-sync' is deprecated, removed automatically. Use 'async-unsafe-read-handling: BUFFERED' for buffered reads instead.", basePath());
        }

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Parallel World Ticking", threads);
        }
    }
}
