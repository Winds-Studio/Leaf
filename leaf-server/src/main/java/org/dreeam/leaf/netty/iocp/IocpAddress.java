package org.dreeam.leaf.netty.iocp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;

final class IocpAddress {
    static final long SIZE = 24;

    private IocpAddress() {
    }

    static MemorySegment allocate(Arena arena, InetSocketAddress address, int socketFamily) {
        if (address.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        MemorySegment segment = arena.allocate(SIZE, 8);
        InetAddress inetAddress = address.getAddress();
        byte[] bytes = inetAddress.getAddress();
        if (socketFamily == IocpNative.AF_INET) {
            if (!(inetAddress instanceof Inet4Address)) {
                throw new IllegalArgumentException("An IPv6 address cannot be used with an IPv4 IOCP socket: " + address);
            }
            segment.set(ValueLayout.JAVA_SHORT, 0, (short) IocpNative.AF_INET);
            segment.set(ValueLayout.JAVA_SHORT, 2, (short) address.getPort());
            MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 8, bytes.length);
            return segment;
        }

        segment.set(ValueLayout.JAVA_SHORT, 0, (short) IocpNative.AF_INET6);
        segment.set(ValueLayout.JAVA_SHORT, 2, (short) address.getPort());
        if (inetAddress.isAnyLocalAddress()) {
            return segment;
        } else if (inetAddress instanceof Inet6Address inet6Address) {
            segment.set(ValueLayout.JAVA_INT, 4, inet6Address.getScopeId());
            MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 8, bytes.length);
        } else {
            segment.set(ValueLayout.JAVA_BYTE, 18, (byte) 0xFF);
            segment.set(ValueLayout.JAVA_BYTE, 19, (byte) 0xFF);
            MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 20, bytes.length);
        }
        return segment;
    }

    static MemorySegment allocateWildcard(Arena arena, int family) {
        byte[] bytes = family == IocpNative.AF_INET ? new byte[4] : new byte[16];
        try {
            return allocate(arena, new InetSocketAddress(InetAddress.getByAddress(bytes), 0), family);
        } catch (UnknownHostException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static InetSocketAddress decode(MemorySegment segment) throws UnknownHostException {
        int family = Short.toUnsignedInt(segment.get(ValueLayout.JAVA_SHORT, 0));
        int port = Short.toUnsignedInt(segment.get(ValueLayout.JAVA_SHORT, 2));
        if (family == IocpNative.AF_INET) {
            byte[] address = new byte[4];
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 8, address, 0, address.length);
            return new InetSocketAddress(InetAddress.getByAddress(address), port);
        }
        if (family != IocpNative.AF_INET6) {
            throw new UnknownHostException("Unexpected native address family " + family);
        }

        byte[] address = new byte[16];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 8, address, 0, address.length);
        if (isIpv4Mapped(address)) {
            byte[] ipv4 = new byte[4];
            System.arraycopy(address, 12, ipv4, 0, ipv4.length);
            return new InetSocketAddress(InetAddress.getByAddress(ipv4), port);
        }
        int scope = segment.get(ValueLayout.JAVA_INT, 4);
        return new InetSocketAddress(Inet6Address.getByAddress(null, address, scope), port);
    }

    private static boolean isIpv4Mapped(byte[] address) {
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xFF && address[11] == (byte) 0xFF;
    }
}
