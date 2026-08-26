package org.dreeam.leaf.netty.iocp;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannelConfig;
import java.io.IOException;
import java.util.Map;

final class IocpSocketChannelConfig extends DefaultChannelConfig implements SocketChannelConfig {
    private final IocpSocketChannel channel;
    private volatile boolean allowHalfClosure;

    IocpSocketChannelConfig(IocpSocketChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return this.getOptions(super.getOptions(), ChannelOption.SO_RCVBUF, ChannelOption.SO_SNDBUF, ChannelOption.TCP_NODELAY, ChannelOption.SO_KEEPALIVE, ChannelOption.SO_REUSEADDR, ChannelOption.SO_LINGER, ChannelOption.IP_TOS, ChannelOption.ALLOW_HALF_CLOSURE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_RCVBUF) return (T) Integer.valueOf(this.getReceiveBufferSize());
        if (option == ChannelOption.SO_SNDBUF) return (T) Integer.valueOf(this.getSendBufferSize());
        if (option == ChannelOption.TCP_NODELAY) return (T) Boolean.valueOf(this.isTcpNoDelay());
        if (option == ChannelOption.SO_KEEPALIVE) return (T) Boolean.valueOf(this.isKeepAlive());
        if (option == ChannelOption.SO_REUSEADDR) return (T) Boolean.valueOf(this.isReuseAddress());
        if (option == ChannelOption.SO_LINGER) return (T) Integer.valueOf(this.getSoLinger());
        if (option == ChannelOption.IP_TOS) return (T) Integer.valueOf(this.getTrafficClass());
        if (option == ChannelOption.ALLOW_HALF_CLOSURE) return (T) Boolean.valueOf(this.isAllowHalfClosure());
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        this.validate(option, value);
        if (option == ChannelOption.SO_RCVBUF) this.setReceiveBufferSize((Integer) value);
        else if (option == ChannelOption.SO_SNDBUF) this.setSendBufferSize((Integer) value);
        else if (option == ChannelOption.TCP_NODELAY) this.setTcpNoDelay((Boolean) value);
        else if (option == ChannelOption.SO_KEEPALIVE) this.setKeepAlive((Boolean) value);
        else if (option == ChannelOption.SO_REUSEADDR) this.setReuseAddress((Boolean) value);
        else if (option == ChannelOption.SO_LINGER) this.setSoLinger((Integer) value);
        else if (option == ChannelOption.IP_TOS) this.setTrafficClass((Integer) value);
        else if (option == ChannelOption.ALLOW_HALF_CLOSURE) this.setAllowHalfClosure((Boolean) value);
        else return super.setOption(option, value);
        return true;
    }

    @Override
    public boolean isTcpNoDelay() {
        return this.getBoolean(IocpNative.IPPROTO_TCP, IocpNative.TCP_NODELAY);
    }

    @Override
    public IocpSocketChannelConfig setTcpNoDelay(boolean value) {
        this.setBoolean(IocpNative.IPPROTO_TCP, IocpNative.TCP_NODELAY, value);
        return this;
    }

    @Override
    public int getSoLinger() {
        try {
            return this.channel.socket.getSoLinger();
        } catch (IOException exception) {
            throw new ChannelException(exception);
        }
    }

    @Override
    public IocpSocketChannelConfig setSoLinger(int value) {
        if (value > 0) {
            // Supporting positive lingers requires an offloaded close state machine that this transport does not provide yet.
            // Maybe added later //TODO Check if this breaks anythinng
            throw new UnsupportedOperationException("IOCP does not support positive SO_LINGER values");
        }
        try {
            this.channel.socket.setSoLinger(value);
            return this;
        } catch (IOException exception) {
            throw new ChannelException(exception);
        }
    }

    @Override
    public int getSendBufferSize() {
        return this.getInt(IocpNative.SOL_SOCKET, IocpNative.SO_SNDBUF);
    }

    @Override
    public IocpSocketChannelConfig setSendBufferSize(int value) {
        this.setInt(IocpNative.SOL_SOCKET, IocpNative.SO_SNDBUF, value);
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return this.getInt(IocpNative.SOL_SOCKET, IocpNative.SO_RCVBUF);
    }

    @Override
    public IocpSocketChannelConfig setReceiveBufferSize(int value) {
        this.setInt(IocpNative.SOL_SOCKET, IocpNative.SO_RCVBUF, value);
        return this;
    }

    @Override
    public boolean isKeepAlive() {
        return this.getBoolean(IocpNative.SOL_SOCKET, IocpNative.SO_KEEPALIVE);
    }

    @Override
    public IocpSocketChannelConfig setKeepAlive(boolean value) {
        this.setBoolean(IocpNative.SOL_SOCKET, IocpNative.SO_KEEPALIVE, value);
        return this;
    }

    @Override
    public int getTrafficClass() {
        return this.channel.socket.family() == IocpNative.AF_INET6 ? this.getInt(IocpNative.IPPROTO_IPV6, IocpNative.IPV6_TCLASS) : this.getInt(IocpNative.IPPROTO_IP, IocpNative.IP_TOS);
    }

    @Override
    public IocpSocketChannelConfig setTrafficClass(int value) {
        if (this.channel.socket.family() == IocpNative.AF_INET6) {
            this.setInt(IocpNative.IPPROTO_IPV6, IocpNative.IPV6_TCLASS, value);
        } else {
            this.setInt(IocpNative.IPPROTO_IP, IocpNative.IP_TOS, value);
        }
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return this.getBoolean(IocpNative.SOL_SOCKET, IocpNative.SO_REUSEADDR);
    }

    @Override
    public IocpSocketChannelConfig setReuseAddress(boolean value) {
        this.setBoolean(IocpNative.SOL_SOCKET, IocpNative.SO_REUSEADDR, value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return this.allowHalfClosure;
    }

    @Override
    public IocpSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    @Override
    protected void autoReadCleared() {
        this.channel.clearRead();
    }

    private int getInt(int level, int option) {
        try {
            return this.channel.socket.getIntOption(level, option);
        } catch (IOException exception) {
            throw new ChannelException(exception);
        }
    }

    private boolean getBoolean(int level, int option) {
        return this.getInt(level, option) != 0;
    }

    private void setInt(int level, int option, int value) {
        try {
            this.channel.socket.setIntOption(level, option, value);
        } catch (IOException exception) {
            throw new ChannelException(exception);
        }
    }

    private void setBoolean(int level, int option, boolean value) {
        this.setInt(level, option, value ? 1 : 0);
    }

    @Override
    public IocpSocketChannelConfig setConnectTimeoutMillis(int value) {
        super.setConnectTimeoutMillis(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setMaxMessagesPerRead(int value) {
        super.setMaxMessagesPerRead(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setWriteSpinCount(int value) {
        super.setWriteSpinCount(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setAllocator(ByteBufAllocator value) {
        super.setAllocator(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator value) {
        super.setRecvByteBufAllocator(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setAutoRead(boolean value) {
        super.setAutoRead(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setAutoClose(boolean value) {
        super.setAutoClose(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setWriteBufferHighWaterMark(int value) {
        super.setWriteBufferHighWaterMark(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setWriteBufferLowWaterMark(int value) {
        super.setWriteBufferLowWaterMark(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark value) {
        super.setWriteBufferWaterMark(value);
        return this;
    }

    @Override
    public IocpSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator value) {
        super.setMessageSizeEstimator(value);
        return this;
    }
}
