package org.leavesmc.leaves.protocol.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.papermc.paper.ServerBuildInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public class ProtocolUtils {

    private static final Function<ByteBuf, RegistryFriendlyByteBuf> bufDecorator = buf -> buf instanceof RegistryFriendlyByteBuf registry ? registry : new RegistryFriendlyByteBuf(buf, MinecraftServer.getServer().registryAccess());
    private static final byte[] EMPTY = new byte[0];

    public static String buildProtocolVersion(String protocol) {
        return protocol + "-leaves-" + ServerBuildInfo.buildInfo().asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE);
    }

    public static void sendEmptyPacket(ServerPlayer player, ResourceLocation id) {
        player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, EMPTY)));
    }

    public static void sendBytebufPacket(@NotNull ServerPlayer player, ResourceLocation id, Consumer<? super RegistryFriendlyByteBuf> consumer) {
        RegistryFriendlyByteBuf buf = decorate(Unpooled.buffer());
        consumer.accept(buf);
        player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, ByteBufUtil.getBytes(buf))));
    }

    public static void sendPayloadPacket(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void sendEmptyPacket(Context context, ResourceLocation id) {
        context.connection().send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, EMPTY)));
    }

    public static void sendBytebufPacket(@NotNull Context context, ResourceLocation id, Consumer<? super RegistryFriendlyByteBuf> consumer) {
        RegistryFriendlyByteBuf buf = decorate(Unpooled.buffer());
        consumer.accept(buf);
        context.connection().send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, ByteBufUtil.getBytes(buf))));
    }

    public static void sendPayloadPacket(Context context, CustomPacketPayload payload) {
        context.connection().send(new ClientboundCustomPayloadPacket(payload));
    }

    public static RegistryFriendlyByteBuf decorate(ByteBuf buf) {
        return bufDecorator.apply(buf);
    }

    public static IdentifierSelector createSelector(ServerCommonPacketListenerImpl common) {
        ServerPlayer player = common instanceof ServerGamePacketListenerImpl game ? game.getPlayer() : null;
        return new IdentifierSelector(new Context(common.profile, common.connection), player);
    }

    public static ByteBuf wrapNullable(byte @Nullable [] data) {
        return data == null ? Unpooled.wrappedBuffer(EMPTY) : Unpooled.wrappedBuffer(data);
    }
}
