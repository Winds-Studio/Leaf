package org.dreeam.leaf.netty.iocp;

import io.netty.channel.IoOps;

abstract class IocpOperation implements IoOps {
    private final long socket;
    private long nativeOperation;
    private boolean completed;
    private boolean cancelRequested;
    IocpOperation nextOutstanding;

    IocpOperation(long socket) {
        this.socket = socket;
    }

    final long socket() {
        return this.socket;
    }

    final long nativeOperation() {
        return this.nativeOperation;
    }

    final void prepareSubmission() {
        if (this.nativeOperation != 0 || this.completed) {
            throw new IllegalStateException("Invalid native IOCP operation state");
        }
    }

    final void nativeOperationSubmitted(long nativeOperation) {
        this.nativeOperation = nativeOperation;
    }

    abstract long submitNative();

    abstract void complete(int bytes, int error);

    abstract void submissionFailed(Throwable cause);

    final void cancel() {
        long operation = this.nativeOperation;
        if (operation == 0 || this.completed || this.cancelRequested) {
            return;
        }
        this.cancelRequested = true;
        int error = IocpNative.cancel(this.socket, operation);
        if (error != 0 && error != IocpErrors.ERROR_NOT_FOUND && error != IocpErrors.ERROR_INVALID_HANDLE) {
            throw IocpErrors.newChannelException("CancelIoEx", error);
        }
    }

    final void markCompleted() {
        this.completed = true;
    }
}
