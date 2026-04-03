package org.leavesmc.leaves.protocol.syncmatica.exchange;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.PlayerIdentifier;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

import java.util.UUID;

public class ModifyExchangeServer extends AbstractExchange {

    private final ServerPlacement placement;
    UUID placementId;

    public ModifyExchangeServer(final UUID placeId, final ExchangeTarget partner) {
        super(partner);
        placementId = placeId;
        placement = SyncmaticaProtocol.getSyncmaticManager().getPlacement(placementId);
    }

    @Override
    public boolean checkPacket(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        return id.equals(PacketType.MODIFY_FINISH.identifier) && checkUUID(packetBuf, placement.getId());
    }

    @Override
    public void handle(final @NotNull ResourceLocation id, final @NotNull FriendlyByteBuf packetBuf) {
        packetBuf.readUUID();
        if (id.equals(PacketType.MODIFY_FINISH.identifier)) {
            CommunicationManager.receivePositionData(placement, packetBuf, getPartner());
            final PlayerIdentifier identifier = SyncmaticaProtocol.getPlayerIdentifierProvider().createOrGet(
                getPartner()
            );
            placement.setLastModifiedBy(identifier);
            SyncmaticaProtocol.getSyncmaticManager().updateServerPlacement();
            succeed();
        }
    }

    @Override
    public void init() {
        if (getPlacement() == null || CommunicationManager.getModifier(placement) != null) {
            close(true);
        } else {
            if (SyncmaticaProtocol.getPlayerIdentifierProvider().createOrGet(this.getPartner()).uuid.equals(placement.getOwner().uuid)) {
                accept();
            } else {
                close(true);
            }
        }
    }

    private void accept() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(placement.getId());
        getPartner().sendPacket(PacketType.MODIFY_REQUEST_ACCEPT.identifier, buf);
        CommunicationManager.setModifier(placement, this);
    }

    @Override
    protected void sendCancelPacket() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(placementId);
        getPartner().sendPacket(PacketType.MODIFY_REQUEST_DENY.identifier, buf);
    }

    public ServerPlacement getPlacement() {
        return placement;
    }

    @Override
    protected void onClose() {
        if (CommunicationManager.getModifier(placement) == this) {
            CommunicationManager.setModifier(placement, null);
        }
    }
}
