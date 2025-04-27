package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.path.PathfindTaskRejectPolicy;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class AsyncPathfinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-pathfinding";
    }

    public static boolean enabled = false;
    public static int asyncPathfindingMaxThreads = 0;
    public static int asyncPathfindingKeepalive = 60;
    public static int asyncPathfindingQueueSize = 0;
    public static PathfindTaskRejectPolicy asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.FLUSH_ALL;
    private static boolean asyncPathfindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath() + ".reject-policy",
            """
            The policy to use when the queue is full and a new task is submitted.
            FLUSH_ALL: All pending tasks will be run on server thread.
            CALLER_RUNS: Newly submitted task will be run on server thread.
            DISCARD: Newly submitted task will be dropped directly.""",
            """
            当队列满时, 新提交的任务将使用以下策略处理.
            FLUSH_ALL: 所有等待中的任务都将在主线程上运行.
            CALLER_RUNS: 新提交的任务将在主线程上运行.
            DISCARD: 新提交的任务会被直接丢弃."""
        );
        if (asyncPathfindingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncPathfindingInitialized = true;

        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        asyncPathfindingMaxThreads = config.getInt(getBasePath() + ".max-threads", asyncPathfindingMaxThreads);
        asyncPathfindingKeepalive = config.getInt(getBasePath() + ".keepalive", asyncPathfindingKeepalive);
        asyncPathfindingQueueSize = config.getInt(getBasePath() + ".queue-size", asyncPathfindingQueueSize);

        if (asyncPathfindingMaxThreads < 0)
            asyncPathfindingMaxThreads = Math.max(availableProcessors + asyncPathfindingMaxThreads, 1);
        else if (asyncPathfindingMaxThreads == 0)
            asyncPathfindingMaxThreads = Math.max(availableProcessors / 4, 1);
        if (!enabled)
            asyncPathfindingMaxThreads = 0;
        else
            LeafConfig.LOGGER.info("Using {} threads for Async Pathfinding", asyncPathfindingMaxThreads);

        if (asyncPathfindingQueueSize <= 0)
            asyncPathfindingQueueSize = asyncPathfindingMaxThreads * 256;

        asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.fromString(config.getString(getBasePath() + ".reject-policy", availableProcessors >= 12 && asyncPathfindingQueueSize < 512 ? PathfindTaskRejectPolicy.FLUSH_ALL.toString() : PathfindTaskRejectPolicy.CALLER_RUNS.toString()));
    }
}
