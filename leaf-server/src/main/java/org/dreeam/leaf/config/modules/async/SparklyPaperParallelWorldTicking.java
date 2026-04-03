package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.world.UnsafeReadPolicy;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

public class SparklyPaperParallelWorldTicking extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".parallel-world-ticking";
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
        config.addCommentRegionBased(getBasePath(), """
                **Experimental feature**
                Enables parallel world ticking to improve performance on multi-core systems.""",
            """
                **实验性功能**
                启用并行世界处理以提高多核 CPU 使用率.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        threads = config.getInt(getBasePath() + ".threads", threads);
        if (enabled) {
            if (threads <= 0) threads = 8;
        } else {
            threads = 0;
        }

        logContainerCreationStacktraces = config.getBoolean(getBasePath() + ".log-container-creation-stacktraces", logContainerCreationStacktraces);
        logContainerCreationStacktraces = enabled && logContainerCreationStacktraces;
        disableHardThrow = config.getBoolean(getBasePath() + ".disable-hard-throw", disableHardThrow);
        disableHardThrow = enabled && disableHardThrow;
        asyncUnsafeReadHandling = UnsafeReadPolicy.fromString(config.getString(getBasePath() + ".async-unsafe-read-handling", asyncUnsafeReadHandling.toString()));

        // Transfer old config
        runAsyncTasksSync = config.getBoolean(getBasePath() + ".run-async-tasks-sync");
        if (runAsyncTasksSync != null && runAsyncTasksSync) {
            LeafConfig.LOGGER.warn("The setting '{}.run-async-tasks-sync' is deprecated, removed automatically. Use 'async-unsafe-read-handling: BUFFERED' for buffered reads instead.", getBasePath());
        }

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Parallel World Ticking", threads);
        }
    }
}
