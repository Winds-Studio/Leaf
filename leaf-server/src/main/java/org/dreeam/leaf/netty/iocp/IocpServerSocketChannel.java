package org.dreeam.leaf.netty.iocp;

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnresolvedAddressException;

public final class IocpServerSocketChannel extends AbstractIocpChannel implements ServerSocketChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final IocpServerSocketChannelConfig config;
    private AcceptOperation acceptOperation;
    private boolean readPending;
    private boolean acceptSubmissionFailed;

    public IocpServerSocketChannel() {
        super(null, newSocket(), false);
        this.config = new IocpServerSocketChannelConfig(this);
    }

    private static IocpSocket newSocket() {
        Iocp.ensureAvailability();
        try {
            return new IocpSocket(IocpSocket.defaultFamily());
        } catch (IOException exception) {
            throw new ChannelException("Unable to create an IOCP server socket", exception);
        }
    }

    @Override
    public ServerSocketChannelConfig config() {
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
        return null;
    }

    @Override
    protected AbstractChannel.AbstractUnsafe newUnsafe() {
        return new IocpServerUnsafe();
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
        this.socket.listen(this.config.getBacklog());
        this.local = this.socket.localAddress();
        this.active = true;
    }

    @Override
    protected void doBeginRead() {
        this.readPending = true;
        this.submitAcceptIfNeeded();
    }

    @Override
    void clearRead() {
        if (this.isRegistered() && !this.eventLoop().inEventLoop()) {
            this.eventLoop().execute(this::clearRead);
            return;
        }
        // A pending AcceptEx cannot be cancelled while keeping the listener reusable.
        // Allowing at most one more accept to complete. Proactor to Reactor limitations, should be fine tho
        this.readPending = false;
    }

    @Override
    void cancelOutstandingOperations() {
        AcceptOperation operation = this.acceptOperation;
        if (operation != null) {
            operation.cancel();
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer outboundBuffer) {
        if (!outboundBuffer.isEmpty()) {
            throw new UnsupportedOperationException("A server socket cannot write messages");
        }
    }

    private void submitAcceptIfNeeded() {
        if (!this.isActive() || this.acceptOperation != null || this.acceptSubmissionFailed || (!this.config.isAutoRead() && !this.readPending)) {
            return;
        }

        IocpSocket accepted = null;
        AcceptOperation operation = null;
        try {
            this.readPending = false;
            accepted = new IocpSocket(this.socket.family());
            operation = new AcceptOperation(accepted);
            this.acceptOperation = operation;
            this.registration().submit(operation);
        } catch (Throwable throwable) {
            this.acceptSubmissionFailed = true;
            if (operation != null && operation.nativeOperation() == 0 && this.acceptOperation == operation) {
                operation.submissionFailed(throwable);
            } else if (operation == null && accepted != null) {
                try {
                    accepted.close();
                } catch (Throwable suppressed) {
                    throwable.addSuppressed(suppressed);
                }
            }
            this.reportAcceptFailure(throwable);
        }
    }

    private void reportAcceptFailure(Throwable failure) {
        try {
            this.pipeline().fireExceptionCaught(failure);
        } catch (Throwable suppressed) {
            if (failure != suppressed) {
                failure.addSuppressed(suppressed);
            }
        }
    }

    private final class AcceptOperation extends IocpOperation {
        private IocpSocket accepted;
        private boolean cleaned;

        AcceptOperation(IocpSocket accepted) {
            super(IocpServerSocketChannel.this.socket.handle());
            this.accepted = accepted;
        }

        @Override
        long submitNative() {
            return IocpNative.submitAccept(this.socket(), this.accepted.handle());
        }

        @Override
        void complete(int bytes, int error) {
            if (IocpServerSocketChannel.this.acceptOperation == this) {
                IocpServerSocketChannel.this.acceptOperation = null;
            }
            if (!IocpServerSocketChannel.this.isActive()) {
                this.closeAccepted();
                return;
            }
            if (error != 0) {
                this.closeAccepted();
                IocpServerSocketChannel.this.reportAcceptFailure(IocpErrors.newIOException("AcceptEx", error));
                IocpServerSocketChannel.this.submitAcceptIfNeeded();
                return;
            }

            IocpSocket accepted = this.accepted;
            try {
                accepted.updateAcceptContext(IocpServerSocketChannel.this.socket);
                InetSocketAddress local = accepted.localAddress();
                InetSocketAddress remote = accepted.remoteAddress();
                IocpSocketChannel child = new IocpSocketChannel(IocpServerSocketChannel.this, accepted, local, remote);
                this.accepted = null;
                this.cleaned = true;
                IocpServerSocketChannel.this.pipeline().fireChannelRead(child);
                IocpServerSocketChannel.this.pipeline().fireChannelReadComplete();
            } catch (Throwable throwable) {
                this.closeAccepted();
                IocpServerSocketChannel.this.reportAcceptFailure(throwable);
            }
            IocpServerSocketChannel.this.submitAcceptIfNeeded();
        }

        @Override
        void submissionFailed(Throwable cause) {
            IocpServerSocketChannel.this.acceptSubmissionFailed = true;
            if (IocpServerSocketChannel.this.acceptOperation == this) {
                IocpServerSocketChannel.this.acceptOperation = null;
            }
            this.closeAccepted();
        }

        private void closeAccepted() {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            IocpSocket socket = this.accepted;
            this.accepted = null;
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException exception) {
                    IocpServerSocketChannel.this.reportAcceptFailure(exception);
                }
            }
        }
    }

    private final class IocpServerUnsafe extends IocpUnsafe {
    }
}
