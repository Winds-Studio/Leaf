package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.world.UnsafeReadPolicy;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "parallel-world-ticking", comments = {
    """
        **Experimental feature**
        Enables parallel world ticking to improve performance on multi-core systems.""",
    """
        **实验性功能**
        启用并行世界处理以提高多核 CPU 使用率."""
})
public class SparklyPaperParallelWorldTicking implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static @Experimental boolean enabled = false;

    @ConfigInfo(name = "threads")
    public static int rawThreads = 8;

    @ConfigInfo(name = "log-container-creation-stacktraces")
    public static boolean logContainerCreationStacktraces = false;

    @ConfigInfo(name = "disable-hard-throw")
    public static boolean disableHardThrow = false;

    @ConfigInfo(name = "async-unsafe-read-handling")
    public static String rawAsyncUnsafeReadHandling = "DISABLED";

    public static @DoNotLoad UnsafeReadPolicy asyncUnsafeReadHandling = UnsafeReadPolicy.DISABLED;

    // The thread count after normalization, 0 while parallel world ticking is disabled.
    public static @DoNotLoad int threads = 0;

    @Override
    public void onLoaded() {
        // The readers of logContainerCreationStacktraces and disableHardThrow also check enabled.
        threads = enabled ? (rawThreads <= 0 ? 8 : rawThreads) : 0;

        asyncUnsafeReadHandling = UnsafeReadPolicy.fromString(rawAsyncUnsafeReadHandling);

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Parallel World Ticking", threads);
        }
    }
}
