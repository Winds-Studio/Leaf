package org.dreeam.leaf.async;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public final class ShutdownExecutors {

    public static final Logger LOGGER = LogManager.getLogger("Leaf");

    private ShutdownExecutors() {
    }

    public static void shutdown() {
        LOGGER.info("Waiting for player I/O executor to shutdown...");
        AsyncPlayerDataSaving.IO_POOL.shutdown();
        try {
            AsyncPlayerDataSaving.IO_POOL.awaitTermination(60L, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        LOGGER.info("Waiting for async executor to shutdown...");
        GlobalDispatcher.INSTANCE.shutdown();
        try {
            GlobalDispatcher.INSTANCE.awaitTermination(30L, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }
}
