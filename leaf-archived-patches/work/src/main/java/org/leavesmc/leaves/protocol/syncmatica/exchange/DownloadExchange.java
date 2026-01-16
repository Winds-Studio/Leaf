package org.leavesmc.leaves.protocol.syncmatica.exchange;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.MessageType;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class DownloadExchange extends AbstractExchange {

    private final ServerPlacement toDownload;
    private final OutputStream outputStream;
    private final MessageDigest md5;
    private final File downloadFile;
    private int bytesSent;

    public DownloadExchange(final ServerPlacement syncmatic, final File downloadFile, final ExchangeTarget partner) throws IOException, NoSuchAlgorithmException {
        super(partner);
        this.downloadFile = downloadFile;
        final OutputStream os = new FileOutputStream(downloadFile);
        toDownload = syncmatic;
        md5 = MessageDigest.getInstance("MD5");
        outputStream = new DigestOutputStream(os, md5);
    }

    @Override
    public boolean checkPacket(final @NotNull ResourceLocation id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.SEND_LITEMATIC.identifier)
            || id.equals(PacketType.FINISHED_LITEMATIC.identifier)
            || id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            return checkUUID(packetBuf, toDownload.getId());
        }
        return false;
    }

    @Override
    public void handle(final @NotNull ResourceLocation id, final @NotNull FriendlyByteBuf packetBuf) {
        packetBuf.readUUID();
        if (id.equals(PacketType.SEND_LITEMATIC.identifier)) {
            final int size = packetBuf.readInt();
            bytesSent += size;
            if (SyncmaticaProtocol.isOverQuota(bytesSent)) {
                close(true);
                SyncmaticaProtocol.getCommunicationManager().sendMessage(
                    getPartner(),
                    MessageType.ERROR,
                    "syncmatica.error.cancelled_transmit_exceed_quota"
                );
                return;
            }
            try {
                packetBuf.readBytes(outputStream, size);
            } catch (final IOException e) {
                close(true);
                e.printStackTrace();
                return;
            }
            final FriendlyByteBuf FriendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf.writeUUID(toDownload.getId());
            getPartner().sendPacket(PacketType.RECEIVED_LITEMATIC.identifier, FriendlyByteBuf);
            return;
        }
        if (id.equals(PacketType.FINISHED_LITEMATIC.identifier)) {
            try {
                outputStream.flush();
            } catch (final IOException e) {
                close(false);
                e.printStackTrace();
                return;
            }
            final UUID downloadHash = UUID.nameUUIDFromBytes(md5.digest());
            if (downloadHash.equals(toDownload.getHash())) {
                succeed();
            } else {
                close(false);
            }
            return;
        }
        if (id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            close(false);
        }
    }

    @Override
    public void init() {
        final FriendlyByteBuf FriendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf.writeUUID(toDownload.getId());
        getPartner().sendPacket(PacketType.REQUEST_LITEMATIC.identifier, FriendlyByteBuf);
    }

    @Override
    protected void onClose() {
        getManager();
        CommunicationManager.setDownloadState(toDownload, false);
        try {
            outputStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        if (!isSuccessful() && downloadFile.exists()) {
            downloadFile.delete();
        }
    }

    @Override
    protected void sendCancelPacket() {
        final FriendlyByteBuf FriendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf.writeUUID(toDownload.getId());
        getPartner().sendPacket(PacketType.CANCEL_LITEMATIC.identifier, FriendlyByteBuf);
    }

    public ServerPlacement getPlacement() {
        return toDownload;
    }
}
