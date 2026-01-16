package org.leavesmc.leaves.protocol.chatimage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.leavesmc.leaves.protocol.core.LeavesCustomPayload;

public record FileChannelPayload(String message) implements LeavesCustomPayload {

    @ID
    private static final ResourceLocation ID = ChatImageProtocol.id("get_file_channel");

    @Codec
    private static final StreamCodec<RegistryFriendlyByteBuf, FileChannelPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, FileChannelPayload::message, FileChannelPayload::new
    );
}