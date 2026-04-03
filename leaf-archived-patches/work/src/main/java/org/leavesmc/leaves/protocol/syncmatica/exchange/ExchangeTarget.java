package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.leavesmc.leaves.protocol.syncmatica.FeatureSet;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExchangeTarget {

    private final List<Exchange> ongoingExchanges = new ArrayList<>();
    private final ServerGamePacketListenerImpl client;
    private FeatureSet features;

    public ExchangeTarget(final ServerGamePacketListenerImpl client) {
        this.client = client;
    }

    public void sendPacket(final ResourceLocation id, final FriendlyByteBuf packetBuf) {
        ProtocolUtils.sendPayloadPacket(client.player, new SyncmaticaPayload(id, packetBuf));
    }

    public FeatureSet getFeatureSet() {
        return features;
    }

    public void setFeatureSet(final FeatureSet f) {
        features = f;
    }

    public Collection<Exchange> getExchanges() {
        return ongoingExchanges;
    }
}
