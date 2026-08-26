package org.dreeam.leaf.netty.iocp;

import io.netty.channel.IoEvent;

final class IocpIoEvent implements IoEvent {
    private IocpOperation operation;
    private int bytes;
    private int error;

    IocpIoEvent reset(IocpOperation operation, int bytes, int error) {
        this.operation = operation;
        this.bytes = bytes;
        this.error = error;
        return this;
    }

    IocpOperation operation() {
        return this.operation;
    }

    int bytes() {
        return this.bytes;
    }

    int error() {
        return this.error;
    }

    void clear() {
        this.operation = null;
    }
}
