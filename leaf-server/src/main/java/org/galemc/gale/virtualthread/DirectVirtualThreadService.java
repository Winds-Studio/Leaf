// Gale - virtual thread support

package org.galemc.gale.virtualthread;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * An implementation of {@link VirtualThreadService} that can create virtual threads directly.
 *
 * @author Martijn Muijsers
 */
final class DirectVirtualThreadService extends VirtualThreadService {

    private DirectVirtualThreadService() {
        super();
    }

    @Override
    public @NotNull ThreadFactory createFactory() {
        // Disabled until Minecraft requires servers to have a Java version that can read class files compiled with functionality from Java 19+ on preview / Java 21+ on stable
        //throw new UnsupportedOperationException();
        return Thread.ofVirtual().factory();
    }

    @Override
    public @NotNull Thread start(@NotNull Runnable task) {
        // Disabled until Minecraft requires servers to have a Java version that can read class files compiled with functionality from Java 19+ on preview / Java 21+ on stable
        //throw new UnsupportedOperationException();
        Objects.requireNonNull(task, "The task to start a virtual thread cannot be null");
        return Thread.ofVirtual().start(task);
    }

    /**
     * @return A functional {@link DirectVirtualThreadService}.
     * @throws Throwable If creating virtual threads directly is not supported by the current runtime.
     *                   This could be any {@link Throwable}, including an {@link Exception} or an {@link Error}.
     */
    static @NotNull DirectVirtualThreadService create() throws Throwable {
        // Disabled until Minecraft requires servers to have a Java version that can read class files compiled with functionality from Java 19+ on preview / Java 21+ on stable
        //throw new UnsupportedOperationException();
        var service = new DirectVirtualThreadService();
        // Run some tests to verify
        service.runTest();
        // If we end up here, it works
        return service;
    }
}
