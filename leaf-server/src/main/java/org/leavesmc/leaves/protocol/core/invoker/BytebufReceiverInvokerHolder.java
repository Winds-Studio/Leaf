package org.leavesmc.leaves.protocol.core.invoker;

import net.minecraft.network.FriendlyByteBuf;
import org.leavesmc.leaves.protocol.core.IdentifierSelector;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;

import java.lang.reflect.Method;

public class BytebufReceiverInvokerHolder extends AbstractInvokerHolder<ProtocolHandler.BytebufReceiver> {
    public BytebufReceiverInvokerHolder(LeavesProtocol owner, Method invoker, ProtocolHandler.BytebufReceiver handler) {
        super(owner, invoker, handler, null, handler.stage().identifier(), FriendlyByteBuf.class);
    }

    public boolean invoke(IdentifierSelector selector, FriendlyByteBuf buf) {
        return invoke0(false, selector.select(handler.stage()), buf) instanceof Boolean b && b;
    }
}
