package org.dreeam.leaf.netty.iocp;

import io.netty.channel.ChannelException;
import java.io.IOException;
import java.net.ConnectException;

final class IocpErrors {
    static final int ERROR_INVALID_HANDLE = 6;
    static final int ERROR_OPERATION_ABORTED = 995;
    static final int ERROR_NOT_FOUND = 1168;
    static final int WSAEWOULDBLOCK = 10035;

    private IocpErrors() {
    }

    static IOException newIOException(String operation, int errorCode) {
        return new IOException(operation + " failed with Windows error " + Integer.toUnsignedString(errorCode));
    }

    static ConnectException newConnectException(String operation, int errorCode) {
        return new ConnectException(operation + " failed with Windows error " + Integer.toUnsignedString(errorCode));
    }

    static ChannelException newChannelException(String operation, int errorCode) {
        return new ChannelException(newIOException(operation, errorCode));
    }
}
