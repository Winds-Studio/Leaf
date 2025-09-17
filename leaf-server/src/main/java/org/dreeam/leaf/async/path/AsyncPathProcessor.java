package org.dreeam.leaf.async.path;

import net.minecraft.world.level.pathfinder.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * used to handle the scheduling of async path processing
 */
public final class AsyncPathProcessor {

    private static final String THREAD_PREFIX = "Leaf Async Pathfinding";
    public static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);

    private AsyncPathProcessor() {
    }

    /**
     * takes a possibly unprocessed path, and waits until it is completed
     * the consumer will be immediately invoked if the path is already processed
     * the consumer will always be called on the main thread
     *
     * @param path            a path to wait on
     * @param afterProcessing a consumer to be called
     */
    public static void awaitProcessing(@Nullable Path path, AsyncPath.PostProcess afterProcessing) {
        if (path instanceof AsyncPath asyncPath) {
            asyncPath.schedulePostProcessing(afterProcessing);
        } else {
            afterProcessing.run(path);
        }
    }
}
