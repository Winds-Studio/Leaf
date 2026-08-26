package org.dreeam.leaf.netty.iocp;

import io.netty.channel.ChannelException;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.util.concurrent.ThreadAwareExecutor;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IocpIoHandler implements IoHandler {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(IocpIoHandler.class);
    private static final int MAX_EVENTS = 256;
    private static final Queue<Object> DEAD_HANDLERS = new ConcurrentLinkedQueue<>(); // FIXME: Better UAF prevention handling

    private final ThreadAwareExecutor executor;
    private final long port;
    private final Arena arena;
    private final MemorySegment completions;
    private final LongObjectMap<Registration> registrations = new LongObjectHashMap<>(256);
    private final LongObjectMap<IocpOperation> operations = new LongObjectHashMap<>(4096);
    private final AtomicBoolean wakeupPending = new AtomicBoolean();
    private final IocpIoEvent event = new IocpIoEvent();
    private final Object lifecycleLock = new Object();
    private long nextKey = 1;
    private int outstandingOperations;
    private boolean destroying;
    private volatile boolean destroyed;

    private IocpIoHandler(ThreadAwareExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        Iocp.ensureAvailability();
        long port = IocpNative.createPort(1, MAX_EVENTS);
        if (port == 0) {
            throw IocpErrors.newChannelException("CreateIoCompletionPort", IocpNative.lastError());
        }
        Arena arena = null;
        try {
            arena = Arena.ofShared();
            this.completions = arena.allocate((long) MAX_EVENTS * IocpNative.COMPLETION_SIZE, 8);
            this.arena = arena;
            this.port = port;
        } catch (Throwable throwable) {
            if (arena != null) arena.close();
            IocpNative.closePort(port);
            throw rethrowUnchecked(throwable);
        }
    }

    public static IoHandlerFactory newFactory() {
        Iocp.ensureAvailability();
        return IocpIoHandler::new;
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        this.checkEventLoop();
        if (this.destroying) {
            throw new IllegalStateException("IOCP handler is shutting down");
        }
        if (!(handle instanceof IocpIoHandle iocpHandle)) {
            throw new IllegalArgumentException("Unsupported IOCP handle " + handle.getClass().getName());
        }
        long socket = iocpHandle.nativeHandle();
        if (socket == 0) {
            throw new ChannelException("Cannot register a closed IOCP socket");
        }
        long key = this.nextKey++;
        if (key == 0) {
            key = this.nextKey++;
        }
        Registration registration = new Registration(key, iocpHandle);
        this.registrations.put(key, registration);
        try {
            int error = iocpHandle.associate(this.port, key);
            if (error != 0) {
                throw IocpErrors.newChannelException("CreateIoCompletionPort(associate)", error);
            }
        } catch (Throwable throwable) {
            this.registrations.remove(key);
            try {
                iocpHandle.close();
            } catch (Throwable suppressed) {
                throwable.addSuppressed(suppressed);
            }
            throw rethrowUnchecked(throwable);
        }
        try {
            iocpHandle.registered();
        } catch (Throwable throwable) {
            this.registrations.remove(key);
            try {
                iocpHandle.close();
            } catch (Throwable suppressed) {
                throwable.addSuppressed(suppressed);
            }
            throw rethrowUnchecked(throwable);
        }
        return registration;
    }

    @Override
    public int run(IoHandlerContext context) {
        this.checkEventLoop();
        if (this.destroyed) {
            return 0;
        }
        this.wakeupPending.set(false);
        int timeoutMillis = timeoutMillis(context);
        return this.pollCompletionEvents(timeoutMillis);
    }

    @Override
    public void wakeup() {
        if (this.destroyed || this.executor.isExecutorThread(Thread.currentThread()) || !this.wakeupPending.compareAndSet(false, true)) {
            return;
        }
        int error;
        synchronized (this.lifecycleLock) {
            if (this.destroyed) {
                this.wakeupPending.set(false);
                return;
            }
            error = IocpNative.postWakeup(this.port);
        }
        if (error != 0) {
            this.wakeupPending.set(false);
            if (error != IocpErrors.ERROR_INVALID_HANDLE) {
                LOGGER.warn("Unable to wake IOCP event loop: Windows error {}", Integer.toUnsignedString(error));
            }
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return IocpIoHandle.class.isAssignableFrom(handleType);
    }

    @Override
    public void prepareToDestroy() {
        this.checkEventLoop();
        this.destroying = true;
        for (Registration registration : new ArrayList<>(this.registrations.values())) {
            try {
                registration.handle.close();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to close an IOCP handle during event-loop shutdown", throwable);
                registration.cancel();
            }
        }
    }

    @Override
    public void destroy() {
        this.checkEventLoop();
        this.destroying = true;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (this.outstandingOperations != 0 && System.nanoTime() < deadline) {
            this.pollCompletionEvents(10);
        }
        if (this.outstandingOperations != 0) {
            synchronized (this.lifecycleLock) {
                this.destroyed = true;
            }
            DEAD_HANDLERS.add(this);
            LOGGER.warn("Closing an IOCP port with {} outstanding operation(s), consider reporting this to Leaf", this.outstandingOperations, new Throwable());
            return;
        }

        int error;
        synchronized (this.lifecycleLock) {
            this.destroyed = true;
            error = IocpNative.closePort(this.port);
        }
        if (error != 0) {
            DEAD_HANDLERS.add(this);
            LOGGER.warn("Unable to close IOCP port: Windows error {}, consider reporting this to Leaf", Integer.toUnsignedString(error));
            return;
        }
        this.arena.close();
    }

    private int pollCompletionEvents(int timeoutMillis) {
        int count = IocpNative.poll(this.port, this.completions, MAX_EVENTS, timeoutMillis);
        if (count < 0) {
            int error = -count;
            if (error == IocpErrors.ERROR_INVALID_HANDLE && this.destroying) {
                return 0;
            }
            throw IocpErrors.newChannelException("GetQueuedCompletionStatusEx", error);
        }

        int handled = 0;
        for (int index = 0; index < count; index++) {
            long offset = (long) index * IocpNative.COMPLETION_SIZE;
            long key = this.completions.get(ValueLayout.JAVA_LONG, offset + IocpNative.COMPLETION_KEY_OFFSET);
            long nativeOperation = this.completions.get(ValueLayout.JAVA_LONG, offset + IocpNative.COMPLETION_OPERATION_OFFSET);
            int bytes = this.completions.get(ValueLayout.JAVA_INT, offset + IocpNative.COMPLETION_BYTES_OFFSET);
            int error = this.completions.get(ValueLayout.JAVA_INT, offset + IocpNative.COMPLETION_ERROR_OFFSET);
            if (key == 0 && nativeOperation == 0) {
                continue;
            }

            Registration registration = this.registrations.get(key);
            IocpOperation operation = this.operations.remove(nativeOperation);
            if (operation == null && registration != null) {
                operation = registration.findOperation(nativeOperation);
            }
            if (operation == null) {
                LOGGER.warn("Received an IOCP completion for unknown operation 0x{}", Long.toHexString(nativeOperation));
                continue;
            }

            try {
                if (registration != null) {
                    registration.handle.handle(registration, this.event.reset(operation, bytes, error));
                } else {
                    operation.complete(bytes, error == 0 ? IocpErrors.ERROR_OPERATION_ABORTED : error);
                }
            } catch (Throwable throwable) {
                LOGGER.warn("An IOCP completion callback failed", throwable);
                if (registration != null) {
                    try {
                        registration.handle.close();
                    } catch (Throwable suppressed) {
                        throwable.addSuppressed(suppressed);
                    }
                }
            } finally {
                this.event.clear();
                operation.markCompleted();
                try {
                    IocpNative.operationRelease(nativeOperation);
                } finally {
                    if (registration != null) {
                        registration.operationCompleted(operation);
                    }
                }
            }
            handled++;
        }
        return handled;
    }

    private void checkEventLoop() {
        if (!this.executor.isExecutorThread(Thread.currentThread())) {
            throw new IllegalStateException("IOCP state must be accessed from its owning event-loop thread");
        }
    }

    private static RuntimeException rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new ChannelException(throwable);
    }

    private static int timeoutMillis(IoHandlerContext context) {
        if (!context.canBlock()) {
            return 0;
        }
        long delayNanos = context.delayNanos(System.nanoTime());
        if (delayNanos == Long.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (delayNanos <= 0) {
            return 0;
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(delayNanos);
        if (delayNanos % 1_000_000L != 0) {
            millis++;
        }
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    private final class Registration implements IoRegistration {
        private final long key;
        private final IocpIoHandle handle;
        private IocpOperation outstanding;
        private boolean valid = true;
        private boolean unregistered;

        Registration(long key, IocpIoHandle handle) {
            this.key = key;
            this.handle = handle;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T attachment() {
            return (T) this.handle;
        }

        @Override
        public long submit(IoOps ioOps) {
            IocpIoHandler.this.checkEventLoop();
            if (!this.valid || IocpIoHandler.this.destroying) {
                throw new IllegalStateException("IOCP registration is cancelled");
            }
            if (!(ioOps instanceof IocpOperation operation)) {
                throw new IllegalArgumentException("Unsupported IOCP operation " + ioOps.getClass().getName());
            }
            if (operation.socket() != this.handle.nativeHandle()) {
                throw new IllegalArgumentException("IOCP operation belongs to a different socket");
            }

            operation.prepareSubmission();
            this.link(operation);
            long nativeOperation;
            try {
                nativeOperation = operation.submitNative();
                if (nativeOperation == 0) {
                    throw IocpErrors.newChannelException("submit overlapped operation", IocpNative.lastError());
                }
                operation.nativeOperationSubmitted(nativeOperation);
                IocpIoHandler.this.operations.put(nativeOperation, operation);
                return nativeOperation;
            } catch (Throwable throwable) {
                if (operation.nativeOperation() == 0) {
                    this.unlink(operation);
                    operation.submissionFailed(throwable);
                }
                throw rethrowUnchecked(throwable);
            }
        }

        @Override
        public boolean isValid() {
            return this.valid;
        }

        @Override
        public boolean cancel() {
            IocpIoHandler.this.checkEventLoop();
            if (!this.valid) {
                return this.unregistered;
            }
            this.valid = false;
            for (IocpOperation operation = this.outstanding; operation != null; operation = operation.nextOutstanding) {
                try {
                    operation.cancel();
                } catch (Throwable throwable) {
                    LOGGER.warn("Unable to cancel an IOCP operation", throwable);
                }
            }
            if (this.outstanding == null) {
                this.unregisterNow();
                return true;
            }
            return false;
        }

        void operationCompleted(IocpOperation operation) {
            this.unlink(operation);
            if (!this.valid && this.outstanding == null) {
                this.unregisterNow();
            }
        }

        IocpOperation findOperation(long nativeOperation) {
            for (IocpOperation operation = this.outstanding; operation != null; operation = operation.nextOutstanding) {
                if (operation.nativeOperation() == nativeOperation) {
                    return operation;
                }
            }
            return null;
        }

        private void link(IocpOperation operation) {
            operation.nextOutstanding = this.outstanding;
            this.outstanding = operation;
            IocpIoHandler.this.outstandingOperations++;
        }

        private void unlink(IocpOperation operation) {
            IocpOperation previous = null;
            IocpOperation current = this.outstanding;
            while (current != null) {
                if (current == operation) {
                    if (previous == null) {
                        this.outstanding = current.nextOutstanding;
                    } else {
                        previous.nextOutstanding = current.nextOutstanding;
                    }
                    current.nextOutstanding = null;
                    IocpIoHandler.this.outstandingOperations--;
                    return;
                }
                previous = current;
                current = current.nextOutstanding;
            }
        }

        private void unregisterNow() {
            if (this.unregistered) {
                return;
            }
            this.unregistered = true;
            IocpIoHandler.this.registrations.remove(this.key);
            this.handle.unregistered();
        }
    }
}
