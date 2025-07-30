package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface Exchange {
    ExchangeTarget getPartner();

    boolean checkPacket(ResourceLocation id, FriendlyByteBuf packetBuf);

    void handle(ResourceLocation id, FriendlyByteBuf packetBuf);

    boolean isFinished();

    boolean isSuccessful();

    void close(boolean notifyPartner);

    void init();
}
