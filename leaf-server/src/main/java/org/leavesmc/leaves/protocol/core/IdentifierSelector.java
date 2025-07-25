package org.leavesmc.leaves.protocol.core;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record IdentifierSelector(@Nullable Context context, @Nullable ServerPlayer player) {

    public Object select(ProtocolHandler.Stage stage) {
        return stage == ProtocolHandler.Stage.CONFIGURATION ? context : player;
    }
}