package org.leavesmc.leaves.protocol.core.invoker;

import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;

import java.lang.reflect.Method;

public class PlayerInvokerHolder<T> extends AbstractInvokerHolder<T> {
    public PlayerInvokerHolder(LeavesProtocol owner, Method invoker, T handler) {
        super(owner, invoker, handler, null, ServerPlayer.class);
    }

    public void invoke(ServerPlayer player) {
        invoke0(false, player);
    }
}
