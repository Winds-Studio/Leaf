// Gale - virtual thread support

package org.galemc.gale.virtualthread;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadFactory;

/**
 * An abstract service to create virtual threads.
 *
 * @author Martijn Muijsers
 */
public sealed abstract class VirtualThreadService permits ReflectionVirtualThreadService, DirectVirtualThreadService {

    /**
     * @return A {@link ThreadFactory} that produces virtual threads.
     */
    public abstract @NotNull ThreadFactory createFactory();

    /**
     * @param task The runnable for the thread to execute.
     * @return A virtual thread that has been started with the given task.
     */
    public abstract @NotNull Thread start(Runnable task);

    /**
     * Runs a test on the {@link #createFactory} and {@link #start} methods,
     * which certainly throws some {@link Throwable} if something goes wrong.
     */
    protected void runTest() throws Throwable {
        // This will definitely throw something if it doesn't work
        try {
            this.start(() -> {}).join();
        } catch (InterruptedException ignored) {} // Except InterruptedException, we don't care about that one
        try {
            var thread = this.createFactory().newThread(() -> {});
            thread.start();
            thread.join();
        } catch (InterruptedException ignored) {} // Except InterruptedException, we don't care about that one
        // If we end up here, it works
    }

    private static boolean initialized = false;

    /**
     * The {@link VirtualThreadService} for the current runtime,
     * or null if virtual threads are not supported, or if not {@link #initialized} yet.
     */
    private static @Nullable VirtualThreadService implementation;

    /**
     * @return Whether virtual threads are supported on the current runtime.
     */
    public static boolean isSupported() {
        return get() != null;
    }

    /**
     * @return The {@link VirtualThreadService} for the current runtime,
     * or null if virtual threads are not {@linkplain #isSupported() supported}.
     * <p>
     * This method is thread-safe only after the first time it has been fully run.
     */
    public static @Nullable VirtualThreadService get() {
        if (!initialized) {
            initialized = true;
            try {
                implementation = DirectVirtualThreadService.create();
            } catch (Throwable ignored) {
                try {
                    implementation = ReflectionVirtualThreadService.create();
                } catch (Throwable ignored2) {}
            }
        }
        return implementation;
    }

    /**
     * The minimum major version of Java that is known to support using virtual threads
     * (although possibly behind a feature preview flag).
     */
    public static final int minimumJavaMajorVersionWithFeaturePreview = 19;

    /**
     * The minimum major version of Java that is known to support using virtual threads
     * even without any feature preview flags.
     */
    public static final int minimumJavaMajorVersionWithoutFeaturePreview = 21;
}
