package org.leavesmc.leaves.protocol.jade.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.leavesmc.leaves.protocol.core.LeavesCustomPayload;
import org.leavesmc.leaves.protocol.jade.JadeProtocol;

public record ClientHandshakePayload(String protocolVersion) implements LeavesCustomPayload {

    @ID
    private static final ResourceLocation PACKET_CLIENT_HANDSHAKE = JadeProtocol.id("client_handshake");

    @Codec
    private static final StreamCodec<RegistryFriendlyByteBuf, ClientHandshakePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ClientHandshakePayload::protocolVersion, ClientHandshakePayload::new
    );
}