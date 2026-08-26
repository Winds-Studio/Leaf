package org.dreeam.leaf.netty.iocp;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ServerChannelRecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.ServerSocketChannelConfig;
import java.io.IOException;
import java.util.Map;

final class IocpServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig {
    private final IocpServerSocketChannel channel;
    private volatile int backlog = 200;

    IocpServerSocketChannelConfig(IocpServerSocketChannel channel) {
        super(channel, new ServerChannelRecvByteBufAllocator());
        this.channel = channel;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return this.getOptions(super.getOptions(), ChannelOption.SO_RCVBUF, ChannelOption.SO_REUSEADDR, ChannelOption.SO_BACKLOG);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_RCVBUF) return (T) Integer.valueOf(this.getReceiveBufferSize());
        if (option == ChannelOption.SO_REUSEADDR) return (T) Boolean.valueOf(this.isReuseAddress());
        if (option == ChannelOption.SO_BACKLOG) return (T) Integer.valueOf(this.getBacklog());
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        this.validate(option, value);
        if (option == ChannelOption.SO_RCVBUF) this.setReceiveBufferSize((Integer) value);
        else if (option == ChannelOption.SO_REUSEADDR) this.setReuseAddress((Boolean) value);
        else if (option == ChannelOption.SO_BACKLOG) this.setBacklog((Integer) value);
        else return super.setOption(option, value);
        return true;
    }

    @Override
    public int getBacklog() {
        return this.backlog;
    }

    @Override
    public IocpServerSocketChannelConfig setBacklog(int backlog) {
        if (backlog < 0) throw new IllegalArgumentException("backlog: " + backlog);
        this.backlog = backlog;
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return this.getInt(IocpNative.SOL_SOCKET, IocpNative.SO_REUSEADDR) != 0;
    }

    @Override
    public IocpServerSocketChannelConfig setReuseAddress(boolean value) {
        this.setInt(IocpNative.SOL_SOCKET, IocpNative.SO_REUSEADDR, value ? 1 : 0);
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return this.getInt(IocpNative.SOL_SOCKET, IocpNative.SO_RCVBUF);
    }

    @Override
    public IocpServerSocketChannelConfig setReceiveBufferSize(int value) {
        this.setInt(IocpNative.SOL_SOCKET, IocpNative.SO_RCVBUF, value);
        return this;
    }

    @Override
    public IocpServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
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

    private void setInt(int level, int option, int value) {
        try {
            this.channel.socket.setIntOption(level, option, value);
        } catch (IOException exception) {
            throw new ChannelException(exception);
        }
    }

    @Override public IocpServerSocketChannelConfig setConnectTimeoutMillis(int value) { super.setConnectTimeoutMillis(value); return this; }
    @Override public IocpServerSocketChannelConfig setMaxMessagesPerRead(int value) { super.setMaxMessagesPerRead(value); return this; }
    @Override public IocpServerSocketChannelConfig setWriteSpinCount(int value) { super.setWriteSpinCount(value); return this; }
    @Override public IocpServerSocketChannelConfig setAllocator(ByteBufAllocator value) { super.setAllocator(value); return this; }
    @Override public IocpServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator value) { super.setRecvByteBufAllocator(value); return this; }
    @Override public IocpServerSocketChannelConfig setAutoRead(boolean value) { super.setAutoRead(value); return this; }
    @Override public IocpServerSocketChannelConfig setWriteBufferHighWaterMark(int value) { super.setWriteBufferHighWaterMark(value); return this; }
    @Override public IocpServerSocketChannelConfig setWriteBufferLowWaterMark(int value) { super.setWriteBufferLowWaterMark(value); return this; }
    @Override public IocpServerSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark value) { super.setWriteBufferWaterMark(value); return this; }
    @Override public IocpServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator value) { super.setMessageSizeEstimator(value); return this; }
}
