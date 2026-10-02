package org.leavesmc.leaves.protocol.core.invoker;

import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;

import java.lang.reflect.Method;

public class InitInvokerHolder extends AbstractInvokerHolder<ProtocolHandler.Init> {
    public InitInvokerHolder(LeavesProtocol owner, Method invoker, ProtocolHandler.Init handler) {
        super(owner, invoker, handler, null);
    }

    public void invoke() {
        invoke0(true);
    }
}
