package org.dreeam.leaf.netty.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.concurrent.ScheduledFuture;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;

public final class IocpSocketChannel extends AbstractIocpChannel implements SocketChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_GATHERING_WRITE_BUFFERS = 64;
    private static final int MAX_GATHERING_WRITE_BYTES = 1024 * 1024;

    private final IocpSocketChannelConfig config;
    private ReadOperation readOperation;
    private WriteOperation writeOperation;
    private ConnectOperation connectOperation;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private boolean readPending;
    private boolean readSubmissionInProgress;
    private boolean readLoopInProgress;
    private boolean inputShutdown;
    private boolean outputShutdown;

    public IocpSocketChannel() {
        this(null, newSocket(), null, null, false);
    }

    IocpSocketChannel(ServerSocketChannel parent, IocpSocket socket, InetSocketAddress local, InetSocketAddress remote) {
        this(parent, socket, local, remote, true);
    }

    private IocpSocketChannel(
        ServerSocketChannel parent,
        IocpSocket socket,
        InetSocketAddress local,
        InetSocketAddress remote,
        boolean active
    ) {
        super(parent, socket, active);
        this.local = local;
        this.remote = remote;
        this.config = new IocpSocketChannelConfig(this);
    }

    private static IocpSocket newSocket() {
        Iocp.ensureAvailability();
        try {
            return new IocpSocket(IocpSocket.defaultFamily());
        } catch (IOException exception) {
            throw new ChannelException("Unable to create an IOCP socket", exception);
        }
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public SocketChannelConfig config() {
        return this.config;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    protected AbstractChannel.AbstractUnsafe newUnsafe() {
        return new IocpSocketUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (!(localAddress instanceof InetSocketAddress inetAddress)) {
            throw new IllegalArgumentException("IOCP only supports InetSocketAddress: " + localAddress);
        }
        if (inetAddress.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
        this.socket.bind(inetAddress);
        this.local = this.socket.localAddress();
    }

    @Override
    void connectIocp(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (promise.isDone()) {
            return;
        }
        if (!(remoteAddress instanceof InetSocketAddress inetRemote)) {
            promise.setFailure(new IllegalArgumentException("IOCP only supports InetSocketAddress: " + remoteAddress));
            return;
        }
        if (inetRemote.isUnresolved()) {
            promise.setFailure(new UnresolvedAddressException());
            return;
        }
        if (this.connectPromise != null) {
            promise.setFailure(new IllegalStateException("connection attempt already in progress"));
            return;
        }
        if (!this.isOpen()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        boolean submitted = false;
        try {
            if (localAddress != null) {
                this.doBind(localAddress);
            } else if (this.local == null) {
                this.socket.bindWildcard();
                this.local = this.socket.localAddress();
            }

            ConnectOperation operation = new ConnectOperation(inetRemote);
            this.connectOperation = operation;
            this.connectPromise = promise;
            this.registration().submit(operation);
            submitted = true;
            int timeoutMillis = this.config.getConnectTimeoutMillis();
            if (timeoutMillis > 0) {
                this.connectTimeoutFuture = this.eventLoop().schedule(() -> {
                    if (this.connectPromise == promise && promise.tryFailure(new ConnectTimeoutException("connection timed out: " + inetRemote))) {
                        this.connectPromise = null;
                        operation.cancel();
                        this.unsafe().close(this.voidPromise());
                    }
                }, timeoutMillis, TimeUnit.MILLISECONDS);
            }
            promise.addListener(future -> {
                if (future.isCancelled() && this.connectOperation == operation) {
                    this.connectPromise = null;
                    operation.cancel();
                    this.unsafe().close(this.voidPromise());
                }
            });
        } catch (Throwable throwable) {
            ConnectOperation operation = this.connectOperation;
            if (operation != null && !submitted && operation.nativeOperation() == 0) {
                operation.submissionFailed(throwable);
            } else if (operation != null) {
                operation.cancel();
            }
            this.connectOperation = null;
            this.connectPromise = null;
            promise.tryFailure(throwable);
            this.unsafe().close(this.voidPromise());
        }
    }

    @Override
    protected void doBeginRead() {
        this.readPending = true;
        this.submitReadIfNeeded();
    }

    @Override
    void clearRead() {
        if (this.isRegistered() && !this.eventLoop().inEventLoop()) {
            this.eventLoop().execute(this::clearRead);
            return;
        }
        // Winsock leaves continued socket use undefined after cancelling an overlapped operation.
        // Let the current receive finish its completion, one more accpet is acceptable.
        this.readPending = false;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer outboundBuffer) throws Exception {
        if (this.writeOperation != null || this.outputShutdown) {
            return;
        }
        for (;;) {
            Object current = outboundBuffer.current();
            if (current == null) {
                return;
            }
            ByteBuf buffer = (ByteBuf) current;
            if (!buffer.isReadable()) {
                outboundBuffer.remove();
                continue;
            }

            WriteOperation operation = new WriteOperation();
            try {
                outboundBuffer.forEachFlushedMessage(operation);
                if (!operation.hasBuffers()) {
                    operation.submissionFailed(new IllegalStateException("No readable buffers available for WSASend"));
                    return;
                }
                this.writeOperation = operation;
                this.registration().submit(operation);
            } catch (Exception | Error e) {
                if (operation.nativeOperation() == 0) operation.submissionFailed(e);
                throw e;
            }
            return;
        }
    }

    @Override
    protected Object filterOutboundMessage(Object message) {
        if (!(message instanceof ByteBuf buffer)) {
            throw new UnsupportedOperationException("IOCP only supports ByteBuf writes: " + message.getClass().getName());
        }
        if (buffer.isDirect() && buffer.hasMemoryAddress()) {
            return buffer;
        }
        ByteBuf direct = this.alloc().directBuffer(buffer.readableBytes());
        direct.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
        ReferenceCountUtil.release(message);
        return direct;
    }

    @Override
    protected void doShutdownOutput() throws Exception {
        if (!this.outputShutdown) {
            this.socket.shutdown(1);
            this.outputShutdown = true;
        }
    }

    @Override
    void cancelOutstandingOperations() {
        if (this.readOperation != null) this.readOperation.cancel();
        if (this.writeOperation != null) this.writeOperation.cancel();
        if (this.connectOperation != null) this.connectOperation.cancel();
    }

    @Override
    public boolean isInputShutdown() {
        return this.inputShutdown || !this.isOpen();
    }

    @Override
    public ChannelFuture shutdownInput() {
        return this.shutdownInput(this.newPromise());
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise promise) {
        Runnable task = () -> {
            if (this.inputShutdown) {
                promise.trySuccess();
                return;
            }
            try {
                this.socket.shutdown(0);
                this.inputShutdown = true;
                // Do not CancelIoEx the pending receive: output may remain usable after a half-close.
                this.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                promise.trySuccess();
            } catch (Throwable throwable) {
                promise.tryFailure(throwable);
            }
        };
        if (this.eventLoop().inEventLoop()) task.run();
        else this.eventLoop().execute(task);
        return promise;
    }

    @Override
    public boolean isOutputShutdown() {
        return this.outputShutdown || !this.isOpen();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return this.shutdownOutput(this.newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        Runnable task = () -> ((AbstractUnsafe) this.unsafe()).shutdownOutput(promise);
        if (this.eventLoop().inEventLoop()) task.run();
        else this.eventLoop().execute(task);
        return promise;
    }

    @Override
    public boolean isShutdown() {
        return this.isInputShutdown() && this.isOutputShutdown();
    }

    @Override
    public ChannelFuture shutdown() {
        return this.shutdown(this.newPromise());
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        ChannelPromise outputPromise = this.newPromise();
        this.shutdownOutput(outputPromise);
        outputPromise.addListener(outputFuture -> {
            Throwable outputCause = outputFuture.cause();
            ChannelPromise inputPromise = this.newPromise();
            this.shutdownInput(inputPromise);
            inputPromise.addListener(inputFuture -> {
                Throwable inputCause = inputFuture.cause();
                if (outputCause == null && inputCause == null) {
                    promise.trySuccess();
                } else if (outputCause != null) {
                    if (inputCause != null) outputCause.addSuppressed(inputCause);
                    promise.tryFailure(outputCause);
                } else {
                    promise.tryFailure(inputCause);
                }
            });
        });
        return promise;
    }

    private void submitReadIfNeeded() {
        if (!this.isActive() || this.inputShutdown || this.readOperation != null || this.readSubmissionInProgress || this.readLoopInProgress || (!this.config.isAutoRead() && !this.readPending)) {
            return;
        }
        this.readSubmissionInProgress = true;
        try {
            this.submitRead();
        } finally {
            this.readSubmissionInProgress = false;
        }
    }

    private void submitRead() {
        RecvByteBufAllocator.Handle allocatorHandle = null;
        ByteBuf buffer = null;
        ReadOperation operation = null;
        boolean allocatorReset = false;
        try {
            allocatorHandle = this.iocpUnsafe().recvBufAllocHandle();
            allocatorHandle.reset(this.config);
            allocatorReset = true;
            buffer = this.allocateReadBuffer(allocatorHandle);
            int length = buffer.writableBytes();
            allocatorHandle.attemptedBytesRead(length);
            operation = new ReadOperation(buffer, allocatorHandle, length);
            buffer = null;
            this.readOperation = operation;
            this.registration().submit(operation);
        } catch (Throwable throwable) {
            if (buffer != null) {
                try {
                    buffer.release();
                } catch (Throwable suppressed) {
                    throwable.addSuppressed(suppressed);
                }
            }
            if (operation != null && operation.nativeOperation() == 0) {
                operation.submissionFailed(throwable);
            } else if (operation == null && allocatorReset) {
                try {
                    allocatorHandle.readComplete();
                } catch (Throwable suppressed) {
                    throwable.addSuppressed(suppressed);
                }
            }
            try {
                this.pipeline().fireExceptionCaught(throwable);
            } finally {
                this.unsafe().close(this.voidPromise());
            }
        }
    }

    private ByteBuf allocateReadBuffer(RecvByteBufAllocator.Handle allocatorHandle) {
        ByteBuf buffer = allocatorHandle.allocate(this.alloc());
        try {
            if (!buffer.isDirect() || !buffer.hasMemoryAddress()) {
                int capacity = Math.max(buffer.writableBytes(), allocatorHandle.guess());
                ByteBuf replaced = buffer;
                buffer = null;
                replaced.release();
                buffer = this.alloc().directBuffer(capacity);
            }
            if (!buffer.isWritable()) {
                buffer.ensureWritable(Math.max(1, allocatorHandle.guess()));
            }
            ByteBuf allocated = buffer;
            buffer = null;
            return allocated;
        } finally {
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    private static boolean continueReading(RecvByteBufAllocator.Handle allocatorHandle, boolean forceMaybeMoreData) {
        if (forceMaybeMoreData && allocatorHandle instanceof RecvByteBufAllocator.ExtendedHandle extendedHandle) {
            return extendedHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER);
        }
        return allocatorHandle.continueReading();
    }

    private void submitWriteIfNeeded() {
        ChannelOutboundBuffer outbound = this.unsafe().outboundBuffer();
        if (outbound == null || !this.isOpen()) {
            return;
        }
        try {
            this.doWrite(outbound);
        } catch (Throwable throwable) {
            this.pipeline().fireExceptionCaught(throwable);
            this.unsafe().close(this.voidPromise());
        }
    }

    private final class ReadOperation extends IocpOperation {
        private ByteBuf buffer;
        private final RecvByteBufAllocator.Handle allocatorHandle;
        private final int length;
        private boolean allocatorFinished;
        private boolean cleaned;

        ReadOperation(ByteBuf buffer, RecvByteBufAllocator.Handle allocatorHandle, int length) {
            super(IocpSocketChannel.this.socket.handle());
            this.buffer = buffer;
            this.allocatorHandle = allocatorHandle;
            this.length = length;
        }

        @Override
        long submitNative() {
            return IocpNative.submitRead(this.socket(), this.buffer.memoryAddress() + this.buffer.writerIndex(), this.length);
        }

        @Override
        void complete(int bytes, int error) {
            IocpSocketChannel.this.readOperation = null;
            ByteBuf buffer = this.buffer;
            this.buffer = null;
            this.cleaned = true;
            if (!IocpSocketChannel.this.isActive() || IocpSocketChannel.this.inputShutdown) {
                try {
                    buffer.release();
                } finally {
                    this.finishAllocator();
                }
                return;
            }
            RecvByteBufAllocator.Handle allocatorHandle = this.allocatorHandle;
            boolean close = false;
            boolean readCompleteAttempted = false;
            IocpSocketChannel.this.readLoopInProgress = true;
            try {
                if (error != 0) {
                    throw IocpErrors.newIOException("WSARecv", error);
                }
                if (bytes == 0) {
                    allocatorHandle.lastBytesRead(-1);
                    buffer.release();
                    buffer = null;
                    IocpSocketChannel.this.readPending = false;
                    close = true;
                } else if (bytes < 0 || bytes > this.length) {
                    throw new IOException(
                        "WSARecv completed with invalid byte count " + Integer.toUnsignedString(bytes)
                            + " for a " + this.length + "-byte buffer"
                    );
                } else {
                    allocatorHandle.lastBytesRead(bytes);
                }

                if (!close) {
                    buffer.writerIndex(buffer.writerIndex() + allocatorHandle.lastBytesRead());
                    allocatorHandle.incMessagesRead(1);
                    IocpSocketChannel.this.readPending = false;
                    ByteBuf delivered = buffer;
                    buffer = null;
                    IocpSocketChannel.this.pipeline().fireChannelRead(delivered);
                    // An overlapped receive may complete on a short first fragment. Force one non-blocking probe
                    // before falling back to the allocator's normal attempted-bytes heuristic.
                    close = this.drainReads(allocatorHandle);
                }

                this.finishAllocator();
                readCompleteAttempted = true;
                IocpSocketChannel.this.pipeline().fireChannelReadComplete();
                if (close) {
                    this.closeOnRead();
                }
            } catch (Throwable throwable) {
                if (buffer != null) {
                    try {
                        buffer.release();
                    } catch (Throwable suppressed) {
                        throwable.addSuppressed(suppressed);
                    }
                }
                if (!readCompleteAttempted) {
                    try {
                        this.finishAllocator();
                        IocpSocketChannel.this.pipeline().fireChannelReadComplete();
                    } catch (Throwable suppressed) {
                        throwable.addSuppressed(suppressed);
                    }
                }
                try {
                    IocpSocketChannel.this.pipeline().fireExceptionCaught(throwable);
                } finally {
                    if (IocpSocketChannel.this.isOpen()) {
                        IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
                    }
                }
            } finally {
                IocpSocketChannel.this.readLoopInProgress = false;
                IocpSocketChannel.this.submitReadIfNeeded();
            }
        }

        private boolean drainReads(RecvByteBufAllocator.Handle allocatorHandle) throws IOException {
            boolean forceMaybeMoreData = true;
            while (IocpSocketChannel.this.isActive()
                && !IocpSocketChannel.this.inputShutdown
                && IocpSocketChannel.this.config.isAutoRead()
                && IocpSocketChannel.continueReading(allocatorHandle, forceMaybeMoreData)) {
                forceMaybeMoreData = false;
                ByteBuf buffer = IocpSocketChannel.this.allocateReadBuffer(allocatorHandle);
                try {
                    if (!IocpSocketChannel.this.isActive()
                        || IocpSocketChannel.this.inputShutdown
                        || !IocpSocketChannel.this.config.isAutoRead()) {
                        return false;
                    }

                    int length = buffer.writableBytes();
                    allocatorHandle.attemptedBytesRead(length);
                    int result = IocpSocketChannel.this.socket.recv(
                        buffer.memoryAddress() + buffer.writerIndex(),
                        length
                    );
                    if (result > 0) {
                        if (result > length) {
                            throw new IOException(
                                "recv completed with invalid byte count " + Integer.toUnsignedString(result)
                                    + " for a " + length + "-byte buffer"
                            );
                        }
                        allocatorHandle.lastBytesRead(result);
                        buffer.writerIndex(buffer.writerIndex() + result);
                        allocatorHandle.incMessagesRead(1);
                        IocpSocketChannel.this.readPending = false;
                        ByteBuf delivered = buffer;
                        buffer = null;
                        IocpSocketChannel.this.pipeline().fireChannelRead(delivered);
                        continue;
                    }
                    if (result == 0) {
                        allocatorHandle.lastBytesRead(-1);
                        IocpSocketChannel.this.readPending = false;
                        return true;
                    }

                    int recvError = -result;
                    if (recvError == IocpErrors.WSAEWOULDBLOCK) {
                        allocatorHandle.lastBytesRead(0);
                        return false;
                    }
                    throw IocpErrors.newIOException("recv", recvError);
                } finally {
                    if (buffer != null) {
                        buffer.release();
                    }
                }
            }
            return false;
        }

        private void closeOnRead() {
            boolean alreadyShutdown = IocpSocketChannel.this.inputShutdown;
            IocpSocketChannel.this.inputShutdown = true;
            if (!alreadyShutdown && IocpSocketChannel.this.config.isAllowHalfClosure()) {
                IocpSocketChannel.this.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
            } else if (!alreadyShutdown) {
                IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
            }
        }

        @Override
        void submissionFailed(Throwable cause) {
            if (this.cleaned) return;
            this.cleaned = true;
            if (IocpSocketChannel.this.readOperation == this) IocpSocketChannel.this.readOperation = null;
            ByteBuf buffer = this.buffer;
            this.buffer = null;
            if (buffer != null) {
                try {
                    buffer.release();
                } catch (Throwable suppressed) {
                    cause.addSuppressed(suppressed);
                }
            }
            try {
                this.finishAllocator();
            } catch (Throwable suppressed) {
                cause.addSuppressed(suppressed);
            }
        }

        private void finishAllocator() {
            if (this.allocatorFinished) {
                return;
            }
            this.allocatorFinished = true;
            this.allocatorHandle.readComplete();
        }
    }

    private final class WriteOperation extends IocpOperation implements ChannelOutboundBuffer.MessageProcessor {
        private ByteBuf firstBuffer;
        private ByteBuf[] buffers;
        private int bufferCount;
        private int totalLength;
        private boolean cleaned;

        WriteOperation() {
            super(IocpSocketChannel.this.socket.handle());
        }

        @Override
        public boolean processMessage(Object message) {
            if (this.bufferCount == MAX_GATHERING_WRITE_BUFFERS || this.totalLength == MAX_GATHERING_WRITE_BYTES) {
                return false;
            }
            if (!(message instanceof ByteBuf buffer) || !buffer.isDirect() || !buffer.hasMemoryAddress()) {
                throw new IllegalStateException("IOCP outbound message was not a direct addressable ByteBuf");
            }
            int readableBytes = buffer.readableBytes();
            if (readableBytes == 0) {
                return true;
            }

            int length = Math.min(readableBytes, MAX_GATHERING_WRITE_BYTES - this.totalLength);
            int index = this.bufferCount;
            if (index == 0) {
                this.firstBuffer = buffer.retain();
            } else {
                ByteBuf[] buffers = this.buffers;
                if (buffers == null) {
                    buffers = new ByteBuf[MAX_GATHERING_WRITE_BUFFERS];
                    buffers[0] = this.firstBuffer;
                    this.buffers = buffers;
                }
                buffers[index] = buffer.retain();
            }
            this.bufferCount = index + 1;
            this.totalLength += length;
            return this.bufferCount < MAX_GATHERING_WRITE_BUFFERS && this.totalLength < MAX_GATHERING_WRITE_BYTES;
        }

        @Override
        long submitNative() {
            if (this.bufferCount == 1) {
                ByteBuf buffer = this.firstBuffer;
                int length = Math.min(buffer.readableBytes(), this.totalLength);
                if (length <= 0) throw new IllegalStateException("IOCP write buffer changed before submission");
                this.totalLength = length;
                return IocpNative.submitWrite(
                    this.socket(),
                    buffer.memoryAddress() + buffer.readerIndex(),
                    length
                );
            }

            // Native copies this descriptor table before returning. The retained ByteBufs keep every data address
            // valid until the corresponding completion (including a CancelIoEx completion) is dispatched.
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment descriptors = arena.allocate((long) this.bufferCount * IocpNative.WRITE_BUFFER_SIZE, Long.BYTES);
                int remaining = this.totalLength;
                for (int index = 0; index < this.bufferCount; index++) {
                    long offset = (long) index * IocpNative.WRITE_BUFFER_SIZE;
                    ByteBuf buffer = this.buffers[index];
                    int length = Math.min(buffer.readableBytes(), remaining);
                    if (length <= 0) {
                        throw new IllegalStateException("IOCP gathering write buffer changed before submission");
                    }
                    descriptors.set(
                        ValueLayout.JAVA_LONG,
                        offset + IocpNative.WRITE_BUFFER_ADDRESS_OFFSET,
                        buffer.memoryAddress() + buffer.readerIndex()
                    );
                    descriptors.set(
                        ValueLayout.JAVA_INT,
                        offset + IocpNative.WRITE_BUFFER_LENGTH_OFFSET,
                        length
                    );
                    descriptors.set(ValueLayout.JAVA_INT, offset + IocpNative.WRITE_BUFFER_RESERVED_OFFSET, 0);
                    remaining -= length;
                }
                if (remaining != 0) {
                    throw new IllegalStateException("IOCP gathering write buffer lengths changed before submission");
                }
                return IocpNative.submitWritev(this.socket(), descriptors, this.bufferCount);
            }
        }

        @Override
        void complete(int bytes, int error) {
            if (this.cleaned) return;
            try {
                ChannelOutboundBuffer outbound = IocpSocketChannel.this.unsafe().outboundBuffer();
                if (error != 0) {
                    IOException exception = IocpErrors.newIOException("WSASend", error);
                    if (outbound != null) outbound.remove(exception);
                    if (error != IocpErrors.ERROR_OPERATION_ABORTED && IocpSocketChannel.this.isOpen()) {
                        IocpSocketChannel.this.pipeline().fireExceptionCaught(exception);
                        IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
                    }
                    return;
                }

                if (bytes <= 0 || bytes > this.totalLength) {
                    IOException exception = new IOException(
                        "WSASend completed with invalid byte count " + Integer.toUnsignedString(bytes)
                            + " for " + this.totalLength + " submitted bytes"
                    );
                    if (outbound != null) outbound.remove(exception);
                    if (IocpSocketChannel.this.isOpen()) {
                        IocpSocketChannel.this.pipeline().fireExceptionCaught(exception);
                        IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
                    }
                    return;
                }
                if (outbound != null) {
                    outbound.removeBytes(bytes);
                }
            } finally {
                this.cleanup();
                if (IocpSocketChannel.this.writeOperation == this) IocpSocketChannel.this.writeOperation = null;
            }
            IocpSocketChannel.this.submitWriteIfNeeded();
        }

        @Override
        void submissionFailed(Throwable cause) {
            if (IocpSocketChannel.this.writeOperation == this) IocpSocketChannel.this.writeOperation = null;
            this.cleanup();
        }

        boolean hasBuffers() {
            return this.bufferCount != 0;
        }

        private void cleanup() {
            if (this.cleaned) return;
            this.cleaned = true;
            ByteBuf firstBuffer = this.firstBuffer;
            this.firstBuffer = null;
            ByteBuf[] buffers = this.buffers;
            this.buffers = null;
            if (buffers != null) {
                for (int index = 0; index < this.bufferCount; index++) {
                    ByteBuf buffer = buffers[index];
                    buffers[index] = null;
                    ReferenceCountUtil.safeRelease(buffer);
                }
            } else if (firstBuffer != null) {
                ReferenceCountUtil.safeRelease(firstBuffer);
            }
        }
    }

    private final class ConnectOperation extends IocpOperation {
        private final Arena arena = Arena.ofShared();
        private final MemorySegment address;
        private boolean cleaned;

        ConnectOperation(InetSocketAddress requestedRemote) {
            super(IocpSocketChannel.this.socket.handle());
            this.address = IocpAddress.allocate(this.arena, requestedRemote, IocpSocketChannel.this.socket.family());
        }

        @Override
        long submitNative() {
            return IocpNative.submitConnect(this.socket(), this.address);
        }

        @Override
        void complete(int bytes, int error) {
            this.cleanup();
            IocpSocketChannel.this.connectOperation = null;
            ScheduledFuture<?> timeoutFuture = IocpSocketChannel.this.connectTimeoutFuture;
            IocpSocketChannel.this.connectTimeoutFuture = null;
            if (timeoutFuture != null) timeoutFuture.cancel(false);
            ChannelPromise promise = IocpSocketChannel.this.connectPromise;
            IocpSocketChannel.this.connectPromise = null;
            if (promise == null) return;
            if (IocpSocketChannel.this.closeFuture().isDone()) {
                promise.tryFailure(new ClosedChannelException());
                return;
            }
            if (error != 0) {
                promise.tryFailure(IocpErrors.newConnectException("ConnectEx", error));
                IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
                return;
            }
            boolean wasActive = IocpSocketChannel.this.isActive();
            try {
                IocpSocketChannel.this.socket.updateConnectContext();
                IocpSocketChannel.this.local = IocpSocketChannel.this.socket.localAddress();
                IocpSocketChannel.this.remote = IocpSocketChannel.this.socket.remoteAddress();
                IocpSocketChannel.this.active = true;
                if (!promise.trySuccess()) {
                    IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
                    return;
                }
                if (!wasActive) IocpSocketChannel.this.pipeline().fireChannelActive();
            } catch (Throwable throwable) {
                promise.tryFailure(throwable);
                IocpSocketChannel.this.unsafe().close(IocpSocketChannel.this.voidPromise());
            }
        }

        @Override
        void submissionFailed(Throwable cause) {
            this.cleanup();
        }

        private void cleanup() {
            if (this.cleaned) return;
            this.cleaned = true;
            this.arena.close();
        }
    }

    private final class IocpSocketUnsafe extends IocpUnsafe {
    }
}
