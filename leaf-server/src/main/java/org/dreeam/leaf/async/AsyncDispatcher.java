package org.dreeam.leaf.async;

@org.jspecify.annotations.NullMarked
public final class AsyncDispatcher {

    public static final ThreadPool INSTANCE;

    static {
        final String threadsProperty = System.getProperty("leaf.scheduler.threads");
        int numThreads = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
        if (threadsProperty != null) {
            try {
                int i = Integer.parseInt(threadsProperty);
                if (i >= 1) {
                    numThreads = i;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        final String queueProperty = System.getProperty("leaf.scheduler.queue-size");
        int queue = 8192;
        if (queueProperty != null) {
            try {
                int j = Integer.parseInt(queueProperty);
                if (j >= 1) queue = j;
            } catch (NumberFormatException ignored) {
            }
        }
        INSTANCE = new ThreadPool(numThreads,
            queue,
            "Leaf Async Scheduler",
            Thread.NORM_PRIORITY - 1);
    }

    private AsyncDispatcher() {
    }
}
