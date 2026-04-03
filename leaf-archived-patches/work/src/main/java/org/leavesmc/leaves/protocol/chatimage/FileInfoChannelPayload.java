package org.leavesmc.leaves.protocol.chatimage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.leavesmc.leaves.protocol.core.LeavesCustomPayload;

public record FileInfoChannelPayload(String message) implements LeavesCustomPayload {

    @ID
    private static final ResourceLocation ID = ChatImageProtocol.id("file_info");

    @Codec
    private static final StreamCodec<RegistryFriendlyByteBuf, FileInfoChannelPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, FileInfoChannelPayload::message, FileInfoChannelPayload::new
    );
}