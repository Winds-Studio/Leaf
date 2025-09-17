package org.dreeam.leaf.async;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public final class ShutdownExecutors {

    public static final Logger LOGGER = LogManager.getLogger("Leaf");

    private ShutdownExecutors() {
    }

    public static void shutdown() {
        if (GlobalDispatcher.INSTANCE != null) {
            LOGGER.info("Waiting for async executor to shutdown...");
            GlobalDispatcher.INSTANCE.shutdown();
            try {
                GlobalDispatcher.INSTANCE.awaitTermination(30L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
