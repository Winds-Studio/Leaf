package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

public class SparklyPaperParallelWorldTicking extends ConfigModules {

    public String getBasePath() {
        // Corrected path based on your comment
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".parallel-world-ticking";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threads = 8;
    public static boolean logContainerCreationStacktraces = false;
    public static boolean disableHardThrow = false;
    @Deprecated
    public static boolean runAsyncTasksSync = false;
    // STRICT, BUFFERED, DISABLED
    public static String asyncUnsafeReadHandling = "BUFFERED";

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
        asyncUnsafeReadHandling = config.getString(getBasePath() + ".async-unsafe-read-handling", asyncUnsafeReadHandling).toUpperCase();

        if (!asyncUnsafeReadHandling.equals("STRICT") && !asyncUnsafeReadHandling.equals("BUFFERED") && !asyncUnsafeReadHandling.equals("DISABLED")) {
            System.err.println("[Leaf] Invalid value for " + getBasePath() + ".async-unsafe-read-handling: " + asyncUnsafeReadHandling + ". Defaulting to STRICT.");
            asyncUnsafeReadHandling = "STRICT";
        }
        if (!enabled) {
            asyncUnsafeReadHandling = "DISABLED";
        }

        runAsyncTasksSync = config.getBoolean(getBasePath() + ".run-async-tasks-sync", false); // Default to false now
        if (runAsyncTasksSync) {
            System.err.println("[Leaf] WARNING: The setting '" + getBasePath() + ".run-async-tasks-sync' is deprecated. Use 'async-unsafe-read-handling: STRICT' for similar safety checks or 'BUFFERED' for buffered reads.");
        }

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Parallel World Ticking", threads);
        }

        runAsyncTasksSync = enabled && runAsyncTasksSync; // Auto-disable if main feature is off
    }
}
