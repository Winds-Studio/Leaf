package org.dreeam.leaf.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

interface Protocol {

    String namespace();

    List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> c2s();

    List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> s2c();

    void tickServer(MinecraftServer server);

    void tickPlayer(ServerPlayer player);

    void tickTracker(ServerPlayer player);

    void disconnected(ServerPlayer conn);

    void handle(ServerPlayer player, @NotNull LeafCustomPayload payload);
}
