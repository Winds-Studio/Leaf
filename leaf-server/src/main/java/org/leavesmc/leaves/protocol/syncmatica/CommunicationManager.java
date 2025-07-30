package org.leavesmc.leaves.protocol.syncmatica;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.dreeam.leaf.config.modules.network.ProtocolSupport;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.syncmatica.exchange.DownloadExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.Exchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.ExchangeTarget;
import org.leavesmc.leaves.protocol.syncmatica.exchange.ModifyExchangeServer;
import org.leavesmc.leaves.protocol.syncmatica.exchange.UploadExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.VersionHandshakeServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LeavesProtocol.Register(namespace = "syncmatica")
public class CommunicationManager implements LeavesProtocol {

    protected static final Collection<ExchangeTarget> broadcastTargets = new ArrayList<>();
    protected static final Map<UUID, Boolean> downloadState = new HashMap<>();
    protected static final Map<UUID, Exchange> modifyState = new HashMap<>();
    protected static final Rotation[] rotOrdinals = Rotation.values();
    protected static final Mirror[] mirOrdinals = Mirror.values();
    private static final Map<UUID, List<ServerPlacement>> downloadingFile = new HashMap<>();
    private static final Map<ExchangeTarget, ServerPlayer> playerMap = new HashMap<>();

    public CommunicationManager() {
    }

    public static GameProfile getGameProfile(final ExchangeTarget exchangeTarget) {
        return playerMap.get(exchangeTarget).getGameProfile();
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        final ExchangeTarget newPlayer = player.connection.exchangeTarget;
        final VersionHandshakeServer hi = new VersionHandshakeServer(newPlayer);
        playerMap.put(newPlayer, player);
        final GameProfile profile = player.getGameProfile();
        SyncmaticaProtocol.getPlayerIdentifierProvider().updateName(profile.getId(), profile.getName());
        startExchangeUnchecked(hi);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(ServerPlayer player) {
        final ExchangeTarget oldPlayer = player.connection.exchangeTarget;
        final Collection<Exchange> potentialMessageTarget = oldPlayer.getExchanges();
        if (potentialMessageTarget != null) {
            for (final Exchange target : potentialMessageTarget) {
                target.close(false);
                handleExchange(target);
            }
        }
        broadcastTargets.remove(oldPlayer);
        playerMap.remove(oldPlayer);
    }

    @ProtocolHandler.PayloadReceiver(payload = SyncmaticaPayload.class)
    public static void onPacketGet(ServerPlayer player, SyncmaticaPayload payload) {
        onPacket(player.connection.exchangeTarget, payload.packetType(), payload.data());
    }

    public static void onPacket(final @NotNull ExchangeTarget source, final ResourceLocation id, final FriendlyByteBuf packetBuf) {
        Exchange handler = null;
        final Collection<Exchange> potentialMessageTarget = source.getExchanges();
        if (potentialMessageTarget != null) {
            for (final Exchange target : potentialMessageTarget) {
                if (target.checkPacket(id, packetBuf)) {
                    target.handle(id, packetBuf);
                    handler = target;
                    break;
                }
            }
        }
        if (handler == null) {
            handle(source, id, packetBuf);
        } else if (handler.isFinished()) {
            notifyClose(handler);
        }
    }

    protected static void handle(ExchangeTarget source, @NotNull ResourceLocation id, FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.REQUEST_LITEMATIC.identifier)) {
            final UUID syncmaticaId = packetBuf.readUUID();
            final ServerPlacement placement = SyncmaticaProtocol.getSyncmaticManager().getPlacement(syncmaticaId);
            if (placement == null) {
                return;
            }
            final File toUpload = SyncmaticaProtocol.getFileStorage().getLocalLitematic(placement);
            final UploadExchange upload;
            try {
                upload = new UploadExchange(placement, toUpload, source);
            } catch (final FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
            startExchange(upload);
            return;
        }
        if (id.equals(PacketType.REGISTER_METADATA.identifier)) {
            final ServerPlacement placement = receiveMetaData(packetBuf, source);
            if (SyncmaticaProtocol.getSyncmaticManager().getPlacement(placement.getId()) != null) {
                cancelShare(source, placement);
                return;
            }

            final GameProfile profile = playerMap.get(source).getGameProfile();
            final PlayerIdentifier playerIdentifier = SyncmaticaProtocol.getPlayerIdentifierProvider().createOrGet(profile);
            if (!placement.getOwner().equals(playerIdentifier)) {
                placement.setOwner(playerIdentifier);
                placement.setLastModifiedBy(playerIdentifier);
            }

            if (!SyncmaticaProtocol.getFileStorage().getLocalState(placement).isLocalFileReady()) {
                if (SyncmaticaProtocol.getFileStorage().getLocalState(placement) == LocalLitematicState.DOWNLOADING_LITEMATIC) {
                    downloadingFile.computeIfAbsent(placement.getHash(), key -> new ArrayList<>()).add(placement);
                    return;
                }
                try {
                    download(placement, source);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            addPlacement(source, placement);
            return;
        }
        if (id.equals(PacketType.REMOVE_SYNCMATIC.identifier)) {
            final UUID placementId = packetBuf.readUUID();
            final ServerPlacement placement = SyncmaticaProtocol.getSyncmaticManager().getPlacement(placementId);
            if (placement != null) {
                if (!getGameProfile(source).getId().equals(placement.getOwner().uuid)) {
                    return;
                }

                final Exchange modifier = getModifier(placement);
                if (modifier != null) {
                    modifier.close(true);
                    notifyClose(modifier);
                }
                SyncmaticaProtocol.getSyncmaticManager().removePlacement(placement);
                for (final ExchangeTarget client : broadcastTargets) {
                    final FriendlyByteBuf newPacketBuf = new FriendlyByteBuf(Unpooled.buffer());
                    newPacketBuf.writeUUID(placement.getId());
                    client.sendPacket(PacketType.REMOVE_SYNCMATIC.identifier, newPacketBuf);
                }
            }
        }
        if (id.equals(PacketType.MODIFY_REQUEST.identifier)) {
            final UUID placementId = packetBuf.readUUID();
            final ModifyExchangeServer modifier = new ModifyExchangeServer(placementId, source);
            startExchange(modifier);
        }
    }

    protected static void handleExchange(Exchange exchange) {
        if (exchange instanceof DownloadExchange) {
            final ServerPlacement p = ((DownloadExchange) exchange).getPlacement();

            if (exchange.isSuccessful()) {
                addPlacement(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final ServerPlacement placement : downloadingFile.get(p.getHash())) {
                        addPlacement(exchange.getPartner(), placement);
                    }
                }
            } else {
                cancelShare(exchange.getPartner(), p);
                if (downloadingFile.containsKey(p.getHash())) {
                    for (final ServerPlacement placement : downloadingFile.get(p.getHash())) {
                        cancelShare(exchange.getPartner(), placement);
                    }
                }
            }

            downloadingFile.remove(p.getHash());
            return;
        }
        if (exchange instanceof VersionHandshakeServer && exchange.isSuccessful()) {
            broadcastTargets.add(exchange.getPartner());
        }
        if (exchange instanceof ModifyExchangeServer && exchange.isSuccessful()) {
            final ServerPlacement placement = ((ModifyExchangeServer) exchange).getPlacement();
            for (final ExchangeTarget client : broadcastTargets) {
                if (client.getFeatureSet().hasFeature(Feature.MODIFY)) {
                    final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUUID(placement.getId());
                    putPositionData(placement, buf, client);
                    if (client.getFeatureSet().hasFeature(Feature.CORE_EX)) {
                        buf.writeUUID(placement.getLastModifiedBy().uuid);
                        buf.writeUtf(placement.getLastModifiedBy().getName());
                    }
                    client.sendPacket(PacketType.MODIFY.identifier, buf);
                } else {
                    final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUUID(placement.getId());
                    client.sendPacket(PacketType.REMOVE_SYNCMATIC.identifier, buf);
                    sendMetaData(placement, client);
                }
            }
        }
    }

    private static void addPlacement(final ExchangeTarget t, final @NotNull ServerPlacement placement) {
        if (SyncmaticaProtocol.getSyncmaticManager().getPlacement(placement.getId()) != null) {
            cancelShare(t, placement);
            return;
        }
        SyncmaticaProtocol.getSyncmaticManager().addPlacement(placement);
        for (final ExchangeTarget target : broadcastTargets) {
            sendMetaData(placement, target);
        }
    }

    private static void cancelShare(final @NotNull ExchangeTarget source, final @NotNull ServerPlacement placement) {
        final FriendlyByteBuf FriendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf.writeUUID(placement.getId());
        source.sendPacket(PacketType.CANCEL_SHARE.identifier, FriendlyByteBuf);
    }

    public static void sendMetaData(final ServerPlacement metaData, final ExchangeTarget target) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        putMetaData(metaData, buf, target);
        target.sendPacket(PacketType.REGISTER_METADATA.identifier, buf);
    }

    public static void putMetaData(final @NotNull ServerPlacement metaData, final @NotNull FriendlyByteBuf buf, final @NotNull ExchangeTarget exchangeTarget) {
        buf.writeUUID(metaData.getId());

        buf.writeUtf(SyncmaticaProtocol.sanitizeFileName(metaData.getName()));
        buf.writeUUID(metaData.getHash());

        if (exchangeTarget.getFeatureSet().hasFeature(Feature.CORE_EX)) {
            buf.writeUUID(metaData.getOwner().uuid);
            buf.writeUtf(metaData.getOwner().getName());
            buf.writeUUID(metaData.getLastModifiedBy().uuid);
            buf.writeUtf(metaData.getLastModifiedBy().getName());
        }

        putPositionData(metaData, buf, exchangeTarget);
    }

    public static void putPositionData(final @NotNull ServerPlacement metaData, final @NotNull FriendlyByteBuf buf, final @NotNull ExchangeTarget exchangeTarget) {
        buf.writeBlockPos(metaData.getPosition());
        buf.writeUtf(metaData.getDimension());
        buf.writeInt(metaData.getRotation().ordinal());
        buf.writeInt(metaData.getMirror().ordinal());

        if (exchangeTarget.getFeatureSet().hasFeature(Feature.CORE_EX)) {
            if (metaData.getSubRegionData().getModificationData() == null) {
                buf.writeInt(0);
                return;
            }

            final Collection<SubRegionPlacementModification> regionData = metaData.getSubRegionData().getModificationData().values();
            buf.writeInt(regionData.size());

            for (final SubRegionPlacementModification subPlacement : regionData) {
                buf.writeUtf(subPlacement.name);
                buf.writeBlockPos(subPlacement.position);
                buf.writeInt(subPlacement.rotation.ordinal());
                buf.writeInt(subPlacement.mirror.ordinal());
            }
        }
    }

    public static ServerPlacement receiveMetaData(final @NotNull FriendlyByteBuf buf, final @NotNull ExchangeTarget exchangeTarget) {
        final UUID id = buf.readUUID();

        final String fileName = SyncmaticaProtocol.sanitizeFileName(buf.readUtf(32767));
        final UUID hash = buf.readUUID();

        PlayerIdentifier owner = PlayerIdentifier.MISSING_PLAYER;
        PlayerIdentifier lastModifiedBy = PlayerIdentifier.MISSING_PLAYER;

        if (exchangeTarget.getFeatureSet().hasFeature(Feature.CORE_EX)) {
            final PlayerIdentifierProvider provider = SyncmaticaProtocol.getPlayerIdentifierProvider();
            owner = provider.createOrGet(buf.readUUID(), buf.readUtf(32767));
            lastModifiedBy = provider.createOrGet(buf.readUUID(), buf.readUtf(32767));
        }

        final ServerPlacement placement = new ServerPlacement(id, fileName, hash, owner);
        placement.setLastModifiedBy(lastModifiedBy);

        receivePositionData(placement, buf, exchangeTarget);

        return placement;
    }

    public static void receivePositionData(final @NotNull ServerPlacement placement, final @NotNull FriendlyByteBuf buf, final @NotNull ExchangeTarget exchangeTarget) {
        final BlockPos pos = buf.readBlockPos();
        final String dimensionId = buf.readUtf(32767);
        final Rotation rot = rotOrdinals[buf.readInt()];
        final Mirror mir = mirOrdinals[buf.readInt()];
        placement.move(dimensionId, pos, rot, mir);

        if (exchangeTarget.getFeatureSet().hasFeature(Feature.CORE_EX)) {
            final SubRegionData subRegionData = placement.getSubRegionData();
            subRegionData.reset();
            final int limit = buf.readInt();
            for (int i = 0; i < limit; i++) {
                subRegionData.modify(buf.readUtf(32767), buf.readBlockPos(), rotOrdinals[buf.readInt()], mirOrdinals[buf.readInt()]);
            }
        }
    }

    public static void download(final ServerPlacement syncmatic, final ExchangeTarget source) throws NoSuchAlgorithmException, IOException {
        if (!SyncmaticaProtocol.getFileStorage().getLocalState(syncmatic).isReadyForDownload()) {
            throw new IllegalArgumentException(syncmatic.toString() + " is not ready for download local state is: " + SyncmaticaProtocol.getFileStorage().getLocalState(syncmatic).toString());
        }
        final File toDownload = SyncmaticaProtocol.getFileStorage().createLocalLitematic(syncmatic);
        final Exchange downloadExchange = new DownloadExchange(syncmatic, toDownload, source);
        setDownloadState(syncmatic, true);
        startExchange(downloadExchange);
    }

    public static void setDownloadState(final @NotNull ServerPlacement syncmatic, final boolean b) {
        downloadState.put(syncmatic.getHash(), b);
    }

    public static boolean getDownloadState(final @NotNull ServerPlacement syncmatic) {
        return downloadState.getOrDefault(syncmatic.getHash(), false);
    }

    public static void setModifier(final @NotNull ServerPlacement syncmatic, final Exchange exchange) {
        modifyState.put(syncmatic.getHash(), exchange);
    }

    public static Exchange getModifier(final @NotNull ServerPlacement syncmatic) {
        return modifyState.get(syncmatic.getHash());
    }

    public static void startExchange(final @NotNull Exchange newExchange) {
        if (!broadcastTargets.contains(newExchange.getPartner())) {
            throw new IllegalArgumentException(newExchange.getPartner().toString() + " is not a valid ExchangeTarget");
        }
        startExchangeUnchecked(newExchange);
    }

    protected static void startExchangeUnchecked(final @NotNull Exchange newExchange) {
        newExchange.getPartner().getExchanges().add(newExchange);
        newExchange.init();
        if (newExchange.isFinished()) {
            notifyClose(newExchange);
        }
    }

    public static void notifyClose(final @NotNull Exchange e) {
        e.getPartner().getExchanges().remove(e);
        handleExchange(e);
    }

    public void sendMessage(final @NotNull ExchangeTarget client, final MessageType type, final String identifier) {
        if (client.getFeatureSet().hasFeature(Feature.MESSAGE)) {
            final FriendlyByteBuf newPacketBuf = new FriendlyByteBuf(Unpooled.buffer());
            newPacketBuf.writeUtf(type.toString());
            newPacketBuf.writeUtf(identifier);
            client.sendPacket(PacketType.MESSAGE.identifier, newPacketBuf);
        } else if (playerMap.containsKey(client)) {
            final ServerPlayer player = playerMap.get(client);
            player.sendSystemMessage(Component.literal("Syncmatica " + type.toString() + " " + identifier));
        }
    }

    @Override
    public boolean isActive() {
        return ProtocolSupport.syncmaticaProtocol;
    }
}
