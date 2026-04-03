// Gale - virtual thread support

package org.galemc.gale.virtualthread;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * An implementation of {@link VirtualThreadService} that can create virtual threads using Java reflection.
 *
 * @author Martijn Muijsers
 */
final class ReflectionVirtualThreadService extends VirtualThreadService {

    /**
     * The {@link Thread}<code>#ofVirtual()</code> method.
     */
    private final @NotNull Method Thread_ofVirtual_method;

    /**
     * The {@link Thread}<code>.Builder#factory()</code> method.
     */
    private final @NotNull Method Thread_Builder_factory_method;

    /**
     * The {@link Thread}<code>.Builder#start(Runnable)</code> method.
     */
    private final @NotNull Method Thread_Builder_start_method;

    private ReflectionVirtualThreadService() throws Throwable {
        this.Thread_ofVirtual_method = Objects.requireNonNull(Thread.class.getMethod("ofVirtual"));
        // The Thread.Builder class
        var Thread_Builder_class = Objects.requireNonNull(Class.forName("java.lang.Thread$Builder"));
        this.Thread_Builder_factory_method = Objects.requireNonNull(Thread_Builder_class.getMethod("factory"));
        this.Thread_Builder_start_method = Objects.requireNonNull(Thread_Builder_class.getMethod("start", Runnable.class));
    }

    @Override
    public @NotNull ThreadFactory createFactory() {
        try {
            return (ThreadFactory) this.Thread_Builder_factory_method.invoke(this.Thread_ofVirtual_method.invoke(null));
        } catch (Exception e) {
            // This should not be possible because it was tested in create()
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Thread start(@NotNull Runnable task) {
        Objects.requireNonNull(task, "The task to start a virtual thread cannot be null");
        try {
            return (Thread) this.Thread_Builder_start_method.invoke(this.Thread_ofVirtual_method.invoke(null), task);
        } catch (Exception e) {
            // This should not be possible because it was tested in create()
            throw new RuntimeException(e);
        }
    }

    /**
     * @return A functional {@link ReflectionVirtualThreadService}.
     * @throws Throwable If creating virtual threads via reflection is not supported by the current runtime.
     *                   This could be any {@link Throwable}, including an {@link Exception} or an {@link Error}.
     */
    static @NotNull ReflectionVirtualThreadService create() throws Throwable {
        // This will already throw something if the reflection fails
        var service = new ReflectionVirtualThreadService();
        // Run some tests to verify
        service.runTest();
        // If we end up here, it works
        return service;
    }
}
