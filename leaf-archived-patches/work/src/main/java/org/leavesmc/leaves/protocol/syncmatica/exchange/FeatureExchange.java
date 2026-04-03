package org.leavesmc.leaves.protocol.syncmatica.exchange;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.syncmatica.FeatureSet;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

public abstract class FeatureExchange extends AbstractExchange {

    protected FeatureExchange(final ExchangeTarget partner) {
        super(partner);
    }

    @Override
    public boolean checkPacket(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        return id.equals(PacketType.FEATURE_REQUEST.identifier)
            || id.equals(PacketType.FEATURE.identifier);
    }

    @Override
    public void handle(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.FEATURE_REQUEST.identifier)) {
            sendFeatures();
        } else if (id.equals(PacketType.FEATURE.identifier)) {
            final FeatureSet fs = FeatureSet.fromString(packetBuf.readUtf(32767));
            getPartner().setFeatureSet(fs);
            onFeatureSetReceive();
        }
    }

    protected void onFeatureSetReceive() {
        succeed();
    }

    public void requestFeatureSet() {
        getPartner().sendPacket(PacketType.FEATURE_REQUEST.identifier, new FriendlyByteBuf(Unpooled.buffer()));
    }

    private void sendFeatures() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        final FeatureSet fs = SyncmaticaProtocol.getFeatureSet();
        buf.writeUtf(fs.toString(), 32767);
        getPartner().sendPacket(PacketType.FEATURE.identifier, buf);
    }
}
