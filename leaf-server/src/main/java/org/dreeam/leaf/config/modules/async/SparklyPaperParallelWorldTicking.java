package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SparklyPaperParallelWorldTicking extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".sparklypaper-parallel-world-tracking";
    }

    public static boolean enabled = false;
    public static int threads = 8;
    public static boolean logContainerCreationStacktraces = false;
    public static boolean disableHardThrow = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        threads = config.getInt(getBasePath() + ".threads", threads);
        threads = enabled ? threads : 0;
        logContainerCreationStacktraces = config.getBoolean(getBasePath() + ".log-container-creation-stacktraces", logContainerCreationStacktraces);
        logContainerCreationStacktraces = enabled && logContainerCreationStacktraces;
        disableHardThrow = config.getBoolean(getBasePath() + ".disable-hard-throw", disableHardThrow);
        disableHardThrow = enabled && disableHardThrow;
    }
}
