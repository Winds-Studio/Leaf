package org.leavesmc.leaves.protocol.core.invoker;

import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.core.IdentifierSelector;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;

import java.lang.reflect.Method;

public class MinecraftRegisterInvokerHolder extends AbstractInvokerHolder<ProtocolHandler.MinecraftRegister> {
    public MinecraftRegisterInvokerHolder(LeavesProtocol owner, Method invoker, ProtocolHandler.MinecraftRegister handler) {
        super(owner, invoker, handler, null, handler.stage().identifier(), Identifier.class);
    }

    public void invoke(IdentifierSelector selector, Identifier id) {
        invoke0(false, selector.select(handler.stage()), id);
    }
}
