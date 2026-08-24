package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.path.PathfindTaskRejectPolicy;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "async-pathfinding")
public class AsyncPathfinding implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-threads")
    public static int maxThreads = 0;

    @ConfigInfo(name = "keepalive")
    public static int keepalive = 60;

    @ConfigInfo(name = "queue-size")
    public static int queueSize = 0;

    @ConfigInfo(name = "reject-policy", comments = {
        """
            The policy to use when the queue is full and a new task is submitted.
            FLUSH_ALL: All pending tasks will be run on server thread.
            CALLER_RUNS: Newly submitted task will be run on server thread.""",
        """
            当队列满时, 新提交的任务将使用以下策略处理.
            FLUSH_ALL: 所有等待中的任务都将在主线程上运行.
            CALLER_RUNS: 新提交的任务将在主线程上运行."""
    })
    public static String rejectPolicy = "default";

    public static @DoNotLoad PathfindTaskRejectPolicy rejectPolicyType = PathfindTaskRejectPolicy.CALLER_RUNS;

    @Override
    public void onLoaded() {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();

        if (maxThreads <= 0) {
            maxThreads = Math.max(availableProcessors / 4, 1);
        }

        if (!enabled) {
            maxThreads = 0;
        }

        if (queueSize <= 0) {
            queueSize = maxThreads * 256;
        }

        String policyStr = rejectPolicy;

        if ("default".equalsIgnoreCase(policyStr)) {
            policyStr = availableProcessors >= 12 && queueSize < 512
                ? PathfindTaskRejectPolicy.FLUSH_ALL.toString()
                : PathfindTaskRejectPolicy.CALLER_RUNS.toString();
        }

        rejectPolicyType = PathfindTaskRejectPolicy.fromString(policyStr);

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Pathfinding", maxThreads);
            org.dreeam.leaf.async.path.AsyncPathProcessor.init();
        }
    }
}
