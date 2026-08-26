package org.dreeam.leaf.netty.iocp;

import com.destroystokyo.paper.util.SneakyThrow;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

abstract class AbstractIocpChannel extends AbstractChannel {
    final IocpSocket socket;
    volatile boolean active;
    volatile InetSocketAddress local;
    volatile InetSocketAddress remote;
    private IoRegistration registration;
    private boolean registrationCancelInProgress;

    AbstractIocpChannel(Channel parent, IocpSocket socket, boolean active) {
        super(parent);
        this.socket = socket;
        this.active = active;
    }

    @Override
    public final boolean isOpen() {
        return this.socket.isOpen();
    }

    @Override
    public boolean isActive() {
        return this.active && this.socket.isOpen();
    }

    @Override
    protected final boolean isCompatible(EventLoop loop) {
        return loop instanceof IoEventLoop ioEventLoop && ioEventLoop.isCompatible(IocpIoHandle.class);
    }

    @Override
    protected final SocketAddress localAddress0() {
        return this.local;
    }

    @Override
    protected final SocketAddress remoteAddress0() {
        return this.remote;
    }

    @Override
    protected void doRegister(ChannelPromise promise) {
        ((IoEventLoop) this.eventLoop()).register(this.iocpUnsafe()).addListener(future -> {
            if (future.isSuccess()) {
                this.registration = (IoRegistration) future.getNow();
                promise.setSuccess();
            } else {
                promise.setFailure(future.cause());
            }
        });
    }

    @Override
    protected final void doDeregister() {
        IoRegistration registration = this.registration;
        if (registration != null && registration.isValid()) {
            this.registrationCancelInProgress = true;
            try {
                registration.cancel();
            } finally {
                this.registrationCancelInProgress = false;
            }
        }
    }

    @Override
    protected void doDisconnect() {
        this.doClose();
    }

    @Override
    protected void doClose() {
        this.active = false;
        Throwable failure = null;
        try {
            this.cancelOutstandingOperations();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        try {
            this.socket.close();
        } catch (Throwable t) {
            if (failure == null) {
                failure = t;
            } else if (failure != t) {
                failure.addSuppressed(t);
            }
        }
        if (failure != null) {
            SneakyThrow.sneaky(failure);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer outboundBuffer) throws Exception {
        if (!outboundBuffer.isEmpty()) {
            throw new UnsupportedOperationException("This IOCP channel does not support writes");
        }
    }

    final IoRegistration registration() {
        IoRegistration registration = this.registration;
        if (registration == null || !registration.isValid()) {
            throw new ChannelException("IOCP channel is not registered");
        }
        return registration;
    }

    final IocpUnsafe iocpUnsafe() {
        return (IocpUnsafe) this.unsafe();
    }

    void clearRead() {
    }

    void cancelOutstandingOperations() {
    }

    void connectIocp(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException("connect is not supported by this IOCP channel"));
    }

    abstract class IocpUnsafe extends AbstractUnsafe implements IocpIoHandle {
        private ChannelPromise deregisterPromise;

        @Override
        public final long nativeHandle() {
            return AbstractIocpChannel.this.socket.handle();
        }

        @Override
        public final int associate(long port, long key) {
            try {
                return AbstractIocpChannel.this.socket.associate(port, key);
            } catch (IOException exception) {
                throw new ChannelException(exception);
            }
        }

        @Override
        public final void handle(IoRegistration registration, IoEvent event) {
            IocpIoEvent iocpEvent = (IocpIoEvent) event;
            iocpEvent.operation().complete(iocpEvent.bytes(), iocpEvent.error());
        }

        @Override
        public final void close() {
            super.close(AbstractIocpChannel.this.voidPromise());
        }

        @Override
        public final void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            AbstractIocpChannel.this.connectIocp(remoteAddress, localAddress, promise);
        }

        @Override
        public void deregister(ChannelPromise promise) {
            if (!AbstractIocpChannel.this.isRegistered()) {
                super.deregister(promise);
                return;
            }
            if (!promise.setUncancellable()) {
                return;
            }
            if (this.deregisterPromise != null) {
                promise.setFailure(new IllegalStateException("IOCP deregistration is already pending"));
                return;
            }

            // A Windows handle cannot be detached from an IOCP or associated with another one. see MS learn docs
            // Treat an explicit deregistation as a close so Netty can never attempt to register this socket on a different eventloop.
            // TODO: This may break plugins that rely on swapping channels at runtime. (Why did they do this?)
            this.deregisterPromise = promise;
            if (AbstractIocpChannel.this.isOpen()) {
                super.close(AbstractIocpChannel.this.voidPromise());
            }
        }

        @Override
        public final void unregistered() {
            IoRegistration registration = AbstractIocpChannel.this.registration;
            if (registration != null && !AbstractIocpChannel.this.registrationCancelInProgress) {
                // The first wrapper cancellation returned false while overlapped operations were pending. Calling it
                // again after the handler unregisters performs SingleThreadIoEventLoop's registration-count decrement.
                registration.cancel();
            }
            AbstractIocpChannel.this.registration = null;
            ChannelPromise promise = this.deregisterPromise;
            if (promise != null) {
                this.deregisterPromise = null;
                super.deregister(promise);
            }
        }

        @Override
        protected void close(ChannelPromise promise, Throwable cause, ClosedChannelException closeCause) {
            super.close(promise, cause, closeCause);
        }
    }
}
