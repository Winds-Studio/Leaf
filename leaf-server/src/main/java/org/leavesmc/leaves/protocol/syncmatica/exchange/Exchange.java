package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public interface Exchange {
    ExchangeTarget getPartner();

    boolean checkPacket(Identifier id, FriendlyByteBuf packetBuf);

    void handle(Identifier id, FriendlyByteBuf packetBuf);

    boolean isFinished();

    boolean isSuccessful();

    void close(boolean notifyPartner);

    void init();
}
