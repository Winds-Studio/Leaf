
package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class AsyncTargetFinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-target-finding";
    }

    public String getPathfindingPath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-pathfinding";
    }

    public static boolean enabled = false;
    public static boolean pathfinding = false;
    public static boolean goal = false;

    public static int queueSize = 4096;
    public static int threadCount = 0;
    private static boolean asyncTargetFindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                This moves the expensive entity and block search calculations to background thread while
                keeping the actual validation on the main thread.""",
            """
                这会将昂贵的实体目标搜索计算移至后台线程, 同时在主线程上保持实际的实体验证.""");

        if (asyncTargetFindingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncTargetFindingInitialized = true;

        goal = config.getBoolean(getBasePath() + ".enabled", enabled);
        threadCount = config.getInt(getBasePath() + ".thread-count", 0);
        queueSize = config.getInt(getBasePath() + ".queue-size", 0);

        pathfinding = config.getBoolean(getPathfindingPath() + ".enabled", pathfinding);

        if (queueSize <= 0) {
            queueSize = 4096;
        }
        if (threadCount == 0) {
            threadCount = Runtime.getRuntime().availableProcessors();
        }
        enabled = goal || pathfinding;
        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Target Finding", threadCount);
        }
    }
}
