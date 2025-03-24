package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class SparklyPaperParallelWorldTicking extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".parallel-world-tracking";
    } // TODO: Correct config key when stable

    @Experimental
    public static boolean enabled = false;
    public static int threads = 8;
    public static boolean logContainerCreationStacktraces = false;
    public static boolean disableHardThrow = false;
    public static boolean runAsyncTasksSync = false;

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
        threads = enabled ? threads : 0;
        logContainerCreationStacktraces = config.getBoolean(getBasePath() + ".log-container-creation-stacktraces", logContainerCreationStacktraces);
        logContainerCreationStacktraces = enabled && logContainerCreationStacktraces;
        disableHardThrow = config.getBoolean(getBasePath() + ".disable-hard-throw", disableHardThrow);
        disableHardThrow = enabled && disableHardThrow;
        runAsyncTasksSync = config.getBoolean(getBasePath() + ".run-async-tasks-sync", runAsyncTasksSync);
        runAsyncTasksSync = enabled && runAsyncTasksSync;
    }
}
