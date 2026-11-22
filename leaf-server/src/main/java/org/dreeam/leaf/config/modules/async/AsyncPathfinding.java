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
    public static int rawMaxThreads = 0;

    @ConfigInfo(name = "keepalive")
    public static int keepalive = 60;

    @ConfigInfo(name = "queue-size")
    public static int rawQueueSize = 0;

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
    public static String rawRejectPolicy = "default";

    public static @DoNotLoad PathfindTaskRejectPolicy rejectPolicy = PathfindTaskRejectPolicy.CALLER_RUNS;

    // The thread count and queue capacity after normalization, 0 threads while disabled.
    public static @DoNotLoad int maxThreads = 0;
    public static @DoNotLoad int queueSize = 0;

    @Override
    public void onLoaded() {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();

        maxThreads = !enabled ? 0 : rawMaxThreads <= 0 ? Math.max(availableProcessors / 4, 1) : rawMaxThreads;
        queueSize = rawQueueSize <= 0 ? maxThreads * 256 : rawQueueSize;

        String policyStr = rawRejectPolicy;

        if ("default".equalsIgnoreCase(policyStr)) {
            policyStr = availableProcessors >= 12 && queueSize < 512
                ? PathfindTaskRejectPolicy.FLUSH_ALL.toString()
                : PathfindTaskRejectPolicy.CALLER_RUNS.toString();
        }

        rejectPolicy = PathfindTaskRejectPolicy.fromString(policyStr);

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Pathfinding", maxThreads);
            org.dreeam.leaf.async.path.AsyncPathProcessor.init();
        }
    }
}
