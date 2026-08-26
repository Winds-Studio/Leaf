package org.dreeam.leaf.netty.iocp;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

final class IocpSocket {
    private static final Queue<IocpSocket> FAILED_CLOSES = new ConcurrentLinkedQueue<>(); // FIXME: Better UAF prevention

    private final int family;
    private final AtomicLong handle;
    private boolean quarantined;

    IocpSocket(int family) throws IOException {
        this(family, create(family));
        if (family == IocpNative.AF_INET6) {
            try {
                this.setIntOption(IocpNative.IPPROTO_IPV6, IocpNative.IPV6_V6ONLY, 0);
            } catch (IOException exception) {
                try {
                    this.close();
                } catch (IOException suppressed) {
                    exception.addSuppressed(suppressed);
                }
                throw exception;
            }
        }
    }

    IocpSocket(int family, long handle) {
        this.family = family;
        this.handle = new AtomicLong(handle);
    }

    static int defaultFamily() {
        return Boolean.getBoolean("java.net.preferIPv4Stack") ? IocpNative.AF_INET : IocpNative.AF_INET6;
    }

    int family() {
        return this.family;
    }

    long handle() {
        return this.handle.get();
    }

    boolean isOpen() {
        return this.handle.get() != 0;
    }

    synchronized int associate(long port, long key) throws IOException {
        return IocpNative.associate(port, this.requireHandle(), key);
    }

    synchronized void bind(InetSocketAddress address) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            int error = IocpNative.socketBind(this.requireHandle(), IocpAddress.allocate(arena, address, this.family));
            if (error != 0) {
                throw IocpErrors.newIOException("bind", error);
            }
        }
    }

    synchronized void bindWildcard() throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            int error = IocpNative.socketBind(this.requireHandle(), IocpAddress.allocateWildcard(arena, this.family));
            if (error != 0) {
                throw IocpErrors.newIOException("bind", error);
            }
        }
    }

    synchronized void listen(int backlog) throws IOException {
        int error = IocpNative.socketListen(this.requireHandle(), backlog);
        if (error != 0) {
            throw IocpErrors.newIOException("listen", error);
        }
    }

    void updateAcceptContext(IocpSocket listener) throws IOException {
        synchronized (this) {
            synchronized (listener) {
                int error = IocpNative.socketUpdateAcceptContext(this.requireHandle(), listener.requireHandle());
                if (error != 0) {
                    throw IocpErrors.newIOException("SO_UPDATE_ACCEPT_CONTEXT", error);
                }
            }
        }
    }

    synchronized void updateConnectContext() throws IOException {
        int error = IocpNative.socketUpdateConnectContext(this.requireHandle());
        if (error != 0) {
            throw IocpErrors.newIOException("SO_UPDATE_CONNECT_CONTEXT", error);
        }
    }

    InetSocketAddress localAddress() throws IOException {
        return this.address(false);
    }

    InetSocketAddress remoteAddress() throws IOException {
        return this.address(true);
    }

    private synchronized InetSocketAddress address(boolean remote) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment address = arena.allocate(IocpAddress.SIZE, 8);
            int error = IocpNative.socketAddress(this.requireHandle(), remote, address);
            if (error != 0) {
                throw IocpErrors.newIOException(remote ? "getpeername" : "getsockname", error);
            }
            return IocpAddress.decode(address);
        }
    }

    synchronized void shutdown(int how) throws IOException {
        int error = IocpNative.socketShutdown(this.requireHandle(), how);
        if (error != 0) {
            throw IocpErrors.newIOException("shutdown", error);
        }
    }

    synchronized int getIntOption(int level, int option) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment length = arena.allocate(ValueLayout.JAVA_INT);
            length.set(ValueLayout.JAVA_INT, 0, Integer.BYTES);
            int error = IocpNative.socketGetOption(this.requireHandle(), level, option, value, length);
            if (error != 0) {
                throw IocpErrors.newIOException("getsockopt", error);
            }
            return value.get(ValueLayout.JAVA_INT, 0);
        }
    }

    synchronized void setIntOption(int level, int option, int optionValue) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, optionValue);
            int error = IocpNative.socketSetOption(this.requireHandle(), level, option, value, Integer.BYTES);
            if (error != 0) {
                throw IocpErrors.newIOException("setsockopt", error);
            }
        }
    }

    int recv(long bufferAddress, int length) {
        long socket = this.handle.get();
        if (socket == 0) {
            return -IocpErrors.ERROR_INVALID_HANDLE;
        }
        return IocpNative.socketRecv(socket, bufferAddress, length);
    }

    synchronized int getSoLinger() throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(4, 2);
            MemorySegment length = arena.allocate(ValueLayout.JAVA_INT);
            length.set(ValueLayout.JAVA_INT, 0, 4);
            int error = IocpNative.socketGetOption(this.requireHandle(), IocpNative.SOL_SOCKET, IocpNative.SO_LINGER, value, length);
            if (error != 0) {
                throw IocpErrors.newIOException("getsockopt(SO_LINGER)", error);
            }
            return value.get(ValueLayout.JAVA_SHORT, 0) == 0 ? -1 : Short.toUnsignedInt(value.get(ValueLayout.JAVA_SHORT, 2));
        }
    }

    synchronized void setSoLinger(int linger) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(4, 2);
            value.set(ValueLayout.JAVA_SHORT, 0, (short) (linger < 0 ? 0 : 1));
            value.set(ValueLayout.JAVA_SHORT, 2, (short) Math.max(linger, 0));
            int error = IocpNative.socketSetOption(this.requireHandle(), IocpNative.SOL_SOCKET, IocpNative.SO_LINGER, value, 4);
            if (error != 0) {
                throw IocpErrors.newIOException("setsockopt(SO_LINGER)", error);
            }
        }
    }

    synchronized void close() throws IOException {
        long socket = this.handle.getAndSet(0);
        if (socket == 0) {
            return;
        }
        int error = IocpNative.socketClose(socket);
        if (error != 0) {
            this.handle.compareAndSet(0, socket);
            if (!this.quarantined) {
                this.quarantined = true;
                FAILED_CLOSES.add(this);
            }
            throw IocpErrors.newIOException("closesocket", error);
        }
        if (this.quarantined) {
            this.quarantined = false;
            FAILED_CLOSES.remove(this);
        }
    }

    private long requireHandle() throws IOException {
        long socket = this.handle.get();
        if (socket == 0) {
            throw new IOException("IOCP socket is closed");
        }
        return socket;
    }

    private static long create(int family) throws IOException {
        long socket = IocpNative.socketCreate(family);
        if (socket == 0) {
            throw IocpErrors.newIOException("WSASocketW", IocpNative.lastError());
        }
        return socket;
    }
}
