package org.leavesmc.leaves.protocol.syncmatica.exchange;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.FeatureSet;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

import java.util.Collection;

public class VersionHandshakeServer extends FeatureExchange {

    public VersionHandshakeServer(final ExchangeTarget partner) {
        super(partner);
    }

    @Override
    public boolean checkPacket(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        return id.equals(PacketType.REGISTER_VERSION.identifier)
            || super.checkPacket(id, packetBuf);
    }

    @Override
    public void handle(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.REGISTER_VERSION.identifier)) {
            String partnerVersion = packetBuf.readUtf();
            if (partnerVersion.equals("0.0.1")) {
                close(false);
                return;
            }
            final FeatureSet fs = FeatureSet.fromVersionString(partnerVersion);
            if (fs == null) {
                requestFeatureSet();
            } else {
                getPartner().setFeatureSet(fs);
                onFeatureSetReceive();
            }
        } else {
            super.handle(id, packetBuf);
        }

    }

    @Override
    public void onFeatureSetReceive() {
        final FriendlyByteBuf newBuf = new FriendlyByteBuf(Unpooled.buffer());
        final Collection<ServerPlacement> l = SyncmaticaProtocol.getSyncmaticManager().getAll();
        newBuf.writeInt(l.size());
        for (final ServerPlacement p : l) {
            CommunicationManager.putMetaData(p, newBuf, getPartner());
        }
        getPartner().sendPacket(PacketType.CONFIRM_USER.identifier, newBuf);
        succeed();
    }

    @Override
    public void init() {
        final FriendlyByteBuf newBuf = new FriendlyByteBuf(Unpooled.buffer());
        newBuf.writeUtf(SyncmaticaProtocol.PROTOCOL_VERSION);
        getPartner().sendPacket(PacketType.REGISTER_VERSION.identifier, newBuf);
    }
}
