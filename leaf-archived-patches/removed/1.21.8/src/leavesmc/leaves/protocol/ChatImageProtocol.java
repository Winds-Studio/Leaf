// Removed since Leaf 1.21.5, replaced by Leaves's ChatImage protocol support since Leaf 1.21.8

package org.leavesmc.leaves.protocol;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.chatimage.ChatImageIndex;
import org.leavesmc.leaves.protocol.core.LeavesCustomPayload;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.LeavesProtocolManager;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LeavesProtocol(namespace = "chatimage")
public class ChatImageProtocol {

    public static final String PROTOCOL_ID = "chatimage";
    private static final Map<String, HashMap<Integer, String>> SERVER_BLOCK_CACHE = new HashMap<>();
    private static final Map<String, Integer> FILE_COUNT_MAP = new HashMap<>();
    private static final Map<String, List<String>> USER_CACHE_MAP = new HashMap<>();
    public static int MAX_STRING = 532767;
    private static final Gson gson = new Gson();

    public static boolean shouldEnable() {
        return org.dreeam.leaf.config.modules.network.ProtocolSupport.chatImageProtocol;
    }

    public record FileInfoChannelPacket(
        String message) implements LeavesCustomPayload<LeavesProtocolManager.LeavesPayload> {
        private static final ResourceLocation FILE_INFO = ChatImageProtocol.id("file_info");

        @New
        public FileInfoChannelPacket(ResourceLocation id, FriendlyByteBuf buffer) {
            this(buffer.readUtf(MAX_STRING));
        }

        @Override
        public void write(final FriendlyByteBuf buffer) {
            buffer.writeUtf(message(), MAX_STRING);
        }

        @Override
        public @NotNull ResourceLocation id() {
            return FILE_INFO;
        }
    }

    public record DownloadFileChannelPacket(
        String message) implements LeavesCustomPayload<LeavesProtocolManager.LeavesPayload> {
        private static final ResourceLocation DOWNLOAD_FILE_CHANNEL = ChatImageProtocol.id("download_file_channel");

        @New
        public DownloadFileChannelPacket(ResourceLocation id, FriendlyByteBuf buffer) {
            this(buffer.readUtf(MAX_STRING));
        }

        @Override
        public void write(final FriendlyByteBuf buffer) {
            buffer.writeUtf(message(), MAX_STRING);
        }

        @Override
        public @NotNull ResourceLocation id() {
            return DOWNLOAD_FILE_CHANNEL;
        }

    }

    public record FileChannelPacket(
        String message) implements LeavesCustomPayload<LeavesProtocolManager.LeavesPayload> {
        private static final ResourceLocation FILE_CHANNEL = ChatImageProtocol.id("file_channel");

        @New
        public FileChannelPacket(ResourceLocation id, FriendlyByteBuf buffer) {
            this(buffer.readUtf(MAX_STRING));
        }

        @Override
        public void write(final FriendlyByteBuf buffer) {
            buffer.writeUtf(message(), MAX_STRING);
        }

        @Override
        public @NotNull ResourceLocation id() {
            return FILE_CHANNEL;
        }

    }

    @ProtocolHandler.PayloadReceiver(payload = FileChannelPacket.class, payloadId = "file_channel")
    public static void serverFileChannelReceived(ServerPlayer player, String res) {
        ChatImageIndex title = gson.fromJson(res, ChatImageIndex.class);
        HashMap<Integer, String> blocks = SERVER_BLOCK_CACHE.containsKey(title.url) ? SERVER_BLOCK_CACHE.get(title.url) : new HashMap<>();
        blocks.put(title.index, res);
        SERVER_BLOCK_CACHE.put(title.url, blocks);
        FILE_COUNT_MAP.put(title.url, title.total);
        if (title.total == blocks.size()) {
            if (USER_CACHE_MAP.containsKey(title.url)) {
                List<String> names = USER_CACHE_MAP.get(title.url);
                for (String uuid : names) {
                    ServerPlayer serverPlayer = player.server.getPlayerList().getPlayer(UUID.fromString(uuid));
                    if (serverPlayer != null) {
                        sendToPlayer(new FileInfoChannelPacket("true->" + title.url), serverPlayer);
                    }
                }
                USER_CACHE_MAP.put(title.url, Lists.newArrayList());
            }
        }
    }

    @ProtocolHandler.PayloadReceiver(payload = FileInfoChannelPacket.class, payloadId = "file_info")
    public static void serverFileInfoChannelReceived(ServerPlayer player, String url) {
        if (SERVER_BLOCK_CACHE.containsKey(url) && FILE_COUNT_MAP.containsKey(url)) {
            HashMap<Integer, String> list = SERVER_BLOCK_CACHE.get(url);
            Integer total = FILE_COUNT_MAP.get(url);
            if (total == list.size()) {
                for (Map.Entry<Integer, String> entry : list.entrySet()) {
                    sendToPlayer(new DownloadFileChannelPacket(entry.getValue()), player);
                }
                return;
            }
        }
        sendToPlayer(new FileInfoChannelPacket("null->" + url), player);
        List<String> names = USER_CACHE_MAP.containsKey(url) ? USER_CACHE_MAP.get(url) : Lists.newArrayList();
        names.add(player.getStringUUID());
        USER_CACHE_MAP.put(url, names);
    }

    @Contract("_ -> new")
    public static @NotNull ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PROTOCOL_ID, path);
    }

    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        player.connection.send((Packet<?>) payload);
    }
}
