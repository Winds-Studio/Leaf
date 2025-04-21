package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
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
    @Deprecated // "Replaced" by asyncUnsafeReadHandling
    public static boolean runAsyncTasksSync = false; // Keep for potential backward compat or remove

    // STRICT, BUFFERED, DISABLED
    public static String asyncUnsafeReadHandling = "STRICT";

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            """
                **Experimental feature**
                Enables parallel world ticking to improve performance on multi-core systems..""",
            """
                **实验性功能**
                启用并行世界处理以提高多核系统的性能.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        threads = config.getInt(getBasePath() + ".threads", threads);
        threads = enabled ? threads : 0; // Ensure threads is 0 if disabled

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
            // Optionally force STRICT mode if the old setting is true
            // asyncUnsafeReadHandling = "STRICT";
        }
        runAsyncTasksSync = enabled && runAsyncTasksSync; // Auto-disable if main feature is off
    }
}
