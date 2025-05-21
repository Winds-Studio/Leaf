package org.dreeam.leaf.protocol;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Protocols {

    private static final ObjectArrayList<Protocol> PROTOCOLS = new ObjectArrayList<>();

    static void register(Protocol protocol) {
        PROTOCOLS.add(protocol);
    }

    static void unregister(Protocol protocol) {
        PROTOCOLS.remove(protocol);
    }

    public record TypeAndCodec<B extends FriendlyByteBuf, T extends LeafCustomPayload>(LeafCustomPayload.Type<T> type,
                                                                                       StreamCodec<B, T> codec) {
    }

    public static <B extends FriendlyByteBuf> void write(B byteBuf, LeafCustomPayload payload) {
        for (Protocol protocol : PROTOCOLS) {
            if (protocol.namespace().equals(payload.type().id().getNamespace())) {
                encode(byteBuf, payload, protocol);
                return;
            }
        }
    }

    public static void handle(ServerPlayer player, @NotNull DiscardedPayload payload) {
        for (Protocol protocol : PROTOCOLS) {
            if (payload.type().id().getNamespace().equals(protocol.namespace())) {
                var leafCustomPayload = decode(protocol, payload);
                if (leafCustomPayload != null) {
                    protocol.handle(player, leafCustomPayload);
                }
                return;
            }
        }
    }

    public static void tickServer(MinecraftServer server) {
        for (Protocol protocol : PROTOCOLS) {
            protocol.tickServer(server);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        for (Protocol protocol : PROTOCOLS) {
            protocol.tickPlayer(player);
        }
    }

    public static void tickTracker(ServerPlayer player) {
        for (Protocol protocol : PROTOCOLS) {
            protocol.tickTracker(player);
        }
    }

    public static void disconnected(ServerPlayer conn) {
        for (Protocol protocol : PROTOCOLS) {
            protocol.disconnected(conn);
        }
    }

    @Contract("_ -> new")
    public static @NotNull ClientboundCustomPayloadPacket createPacket(LeafCustomPayload payload) {
        return new ClientboundCustomPayloadPacket(payload);
    }

    private static <B extends FriendlyByteBuf> void encode(B byteBuf, LeafCustomPayload payload, Protocol protocol) {
        for (var codec : protocol.s2c()) {
            if (codec.type().id().equals(payload.type().id())) {
                byteBuf.writeResourceLocation(payload.type().id());
                //noinspection unchecked,rawtypes
                ((StreamCodec) codec.codec()).encode(byteBuf, payload);
                return;
            }
        }
    }

    private static @Nullable LeafCustomPayload decode(Protocol protocol, DiscardedPayload payload) {
        for (var packet : protocol.c2s()) {
            if (packet.type().id().equals(payload.type().id())) {
                return packet.codec().decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data())));
            }
        }
        return null;
    }
}
