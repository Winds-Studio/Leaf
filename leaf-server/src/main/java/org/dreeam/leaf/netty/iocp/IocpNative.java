package org.dreeam.leaf.netty.iocp;

import com.destroystokyo.paper.util.SneakyThrow;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class IocpNative {
    static final int COMPLETION_SIZE = 24;
    static final int COMPLETION_KEY_OFFSET = 0;
    static final int COMPLETION_OPERATION_OFFSET = 8;
    static final int COMPLETION_BYTES_OFFSET = 16;
    static final int COMPLETION_ERROR_OFFSET = 20;
    static final int WRITE_BUFFER_SIZE = 16;
    static final int WRITE_BUFFER_ADDRESS_OFFSET = 0;
    static final int WRITE_BUFFER_LENGTH_OFFSET = 8;
    static final int WRITE_BUFFER_RESERVED_OFFSET = 12;

    static final int AF_INET = 2;
    static final int AF_INET6 = 23;

    static final int SOL_SOCKET = 0xFFFF;
    static final int SO_REUSEADDR = 0x0004;
    static final int SO_KEEPALIVE = 0x0008;
    static final int SO_LINGER = 0x0080;
    static final int SO_SNDBUF = 0x1001;
    static final int SO_RCVBUF = 0x1002;
    static final int IPPROTO_IP = 0;
    static final int IP_TOS = 3;
    static final int IPPROTO_TCP = 6;
    static final int TCP_NODELAY = 1;
    static final int IPPROTO_IPV6 = 41;
    static final int IPV6_V6ONLY = 27;
    static final int IPV6_TCLASS = 39;

    private static final Linker LINKER;
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle ABI_VERSION_HANDLE;
    private static final MethodHandle INIT;
    private static final MethodHandle LAST_ERROR;
    private static final MethodHandle CREATE_PORT_EX;
    private static final MethodHandle CLOSE_PORT;
    private static final MethodHandle ASSOCIATE;
    private static final MethodHandle POST_WAKEUP;
    private static final MethodHandle POLL;
    private static final MethodHandle SOCKET_CREATE;
    private static final MethodHandle SOCKET_CLOSE;
    private static final MethodHandle SOCKET_SHUTDOWN;
    private static final MethodHandle SOCKET_BIND;
    private static final MethodHandle SOCKET_LISTEN;
    private static final MethodHandle SOCKET_UPDATE_ACCEPT_CONTEXT;
    private static final MethodHandle SOCKET_UPDATE_CONNECT_CONTEXT;
    private static final MethodHandle SOCKET_ADDRESS;
    private static final MethodHandle SOCKET_SET_OPTION;
    private static final MethodHandle SOCKET_GET_OPTION;
    private static final MethodHandle SOCKET_RECV;
    private static final MethodHandle SUBMIT_ACCEPT;
    private static final MethodHandle SUBMIT_CONNECT;
    private static final MethodHandle SUBMIT_READ;
    private static final MethodHandle SUBMIT_WRITE;
    private static final MethodHandle SUBMIT_WRITEV;
    private static final MethodHandle CANCEL;
    private static final MethodHandle OPERATION_RELEASE;

    static {
        IocpNativeLoader.load();
        LINKER = Linker.nativeLinker();
        LOOKUP = SymbolLookup.loaderLookup();

        ABI_VERSION_HANDLE = bindCritical("iocp_abi_version", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        INIT = bind("iocp_init", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        LAST_ERROR = bindCritical("iocp_last_error", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        CREATE_PORT_EX = bind(
            "iocp_create_port_ex",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        CLOSE_PORT = bind("iocp_close_port", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        ASSOCIATE = bind(
            "iocp_associate",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        POST_WAKEUP = bind("iocp_post_wakeup", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        POLL = bind(
            "iocp_poll",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT
            )
        );
        SOCKET_CREATE = bind("iocp_socket_create", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        SOCKET_CLOSE = bind("iocp_socket_close", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        SOCKET_SHUTDOWN = bind(
            "iocp_socket_shutdown",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );
        SOCKET_BIND = bind(
            "iocp_socket_bind",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );
        SOCKET_LISTEN = bind(
            "iocp_socket_listen",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );
        SOCKET_UPDATE_ACCEPT_CONTEXT = bind(
            "iocp_socket_update_accept_context",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        SOCKET_UPDATE_CONNECT_CONTEXT = bind(
            "iocp_socket_update_connect_context",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
        );
        SOCKET_ADDRESS = bind(
            "iocp_socket_address",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        SOCKET_SET_OPTION = bind(
            "iocp_socket_set_option",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
            )
        );
        SOCKET_GET_OPTION = bind(
            "iocp_socket_get_option",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )
        );
        SOCKET_RECV = bind(
            "iocp_socket_recv",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        SUBMIT_ACCEPT = bind(
            "iocp_submit_accept",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        SUBMIT_CONNECT = bind(
            "iocp_submit_connect",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );
        SUBMIT_READ = bind(
            "iocp_submit_read",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        SUBMIT_WRITE = bind(
            "iocp_submit_write",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        SUBMIT_WRITEV = bind(
            "iocp_submit_writev",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        CANCEL = bind(
            "iocp_cancel",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        OPERATION_RELEASE = bind("iocp_operation_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

        int abiVersion = abiVersion();
        if (abiVersion != IocpNativeLoader.ABI_VERSION) {
            throw new UnsatisfiedLinkError("Unsupported IOCP native ABI " + abiVersion + ", expected " + IocpNativeLoader.ABI_VERSION);
        }
        int initError = init0();
        if (initError != 0) {
            throw new UnsatisfiedLinkError("iocp_init failed with Windows error " + Integer.toUnsignedString(initError));
        }
    }

    private IocpNative() {
    }

    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Missing native symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle bindCritical(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Missing native symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor, Linker.Option.critical(false));
    }

    static void probe() {
        long port = createPort(1, 1);
        try {
            if (port == 0) {
                SneakyThrow.sneaky(IocpErrors.newChannelException("CreateIoCompletionPort", lastError()));
            }
        } finally {
            if (port != 0) {
                try {
                    int error = closePort(port);
                    if (error != 0) {
                        throw IocpErrors.newChannelException("CloseHandle(IOCP probe)", error);
                    }
                } catch (Throwable closeFailure) {
                    SneakyThrow.sneaky(closeFailure);
                }
            }
        }
    }

    static int abiVersion() {
        try {
            return (int) ABI_VERSION_HANDLE.invokeExact();
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    private static int init0() {
        try {
            return (int) INIT.invokeExact();
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int lastError() {
        try {
            return (int) LAST_ERROR.invokeExact();
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long createPort(int concurrency, int maxEvents) {
        try {
            return (long) CREATE_PORT_EX.invokeExact(concurrency, maxEvents);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int closePort(long port) {
        try {
            return (int) CLOSE_PORT.invokeExact(port);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int associate(long port, long socket, long key) {
        try {
            return (int) ASSOCIATE.invokeExact(port, socket, key);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int postWakeup(long port) {
        try {
            return (int) POST_WAKEUP.invokeExact(port);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int poll(long port, MemorySegment completions, int capacity, int timeoutMillis) {
        try {
            return (int) POLL.invokeExact(port, completions, capacity, timeoutMillis);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long socketCreate(int family) {
        try {
            return (long) SOCKET_CREATE.invokeExact(family);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketClose(long socket) {
        try {
            return (int) SOCKET_CLOSE.invokeExact(socket);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketShutdown(long socket, int how) {
        try {
            return (int) SOCKET_SHUTDOWN.invokeExact(socket, how);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketBind(long socket, MemorySegment address) {
        try {
            return (int) SOCKET_BIND.invokeExact(socket, address);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketListen(long socket, int backlog) {
        try {
            return (int) SOCKET_LISTEN.invokeExact(socket, backlog);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketUpdateAcceptContext(long acceptedSocket, long listenerSocket) {
        try {
            return (int) SOCKET_UPDATE_ACCEPT_CONTEXT.invokeExact(acceptedSocket, listenerSocket);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketUpdateConnectContext(long socket) {
        try {
            return (int) SOCKET_UPDATE_CONNECT_CONTEXT.invokeExact(socket);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketAddress(long socket, boolean remote, MemorySegment address) {
        try {
            return (int) SOCKET_ADDRESS.invokeExact(socket, remote ? 1 : 0, address);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketSetOption(long socket, int level, int option, MemorySegment value, int length) {
        try {
            return (int) SOCKET_SET_OPTION.invokeExact(socket, level, option, value, length);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketGetOption(long socket, int level, int option, MemorySegment value, MemorySegment length) {
        try {
            return (int) SOCKET_GET_OPTION.invokeExact(socket, level, option, value, length);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int socketRecv(long socket, long bufferAddress, int length) {
        try {
            return (int) SOCKET_RECV.invokeExact(socket, MemorySegment.ofAddress(bufferAddress), length);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long submitAccept(long listenerSocket, long acceptedSocket) {
        try {
            return (long) SUBMIT_ACCEPT.invokeExact(listenerSocket, acceptedSocket);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long submitConnect(long socket, MemorySegment address) {
        try {
            return (long) SUBMIT_CONNECT.invokeExact(socket, address);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long submitRead(long socket, long bufferAddress, int length) {
        try {
            return (long) SUBMIT_READ.invokeExact(socket, MemorySegment.ofAddress(bufferAddress), length);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long submitWrite(long socket, long bufferAddress, int length) {
        try {
            return (long) SUBMIT_WRITE.invokeExact(socket, MemorySegment.ofAddress(bufferAddress), length);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static long submitWritev(long socket, MemorySegment buffers, int count) {
        try {
            return (long) SUBMIT_WRITEV.invokeExact(socket, buffers, count);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static int cancel(long socket, long operation) {
        try {
            return (int) CANCEL.invokeExact(socket, operation);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    static void operationRelease(long operation) {
        try {
            OPERATION_RELEASE.invokeExact(operation);
        } catch (Throwable throwable) {
            throw rethrow(throwable);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Unexpected FFM invocation failure", t);
    }
}
