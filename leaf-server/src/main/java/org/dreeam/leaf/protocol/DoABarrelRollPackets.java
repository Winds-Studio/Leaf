package org.dreeam.leaf.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DoABarrelRollPackets {

    private static <T extends LeafCustomPayload> LeafCustomPayload.@NotNull Type<T> createType(String path) {
        return new LeafCustomPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DoABarrelRollProtocol.NAMESPACE, path));
    }

    public record ConfigResponseC2SPacket(int protocolVersion, boolean success) implements LeafCustomPayload {
        public static final Type<ConfigResponseC2SPacket> TYPE = createType("config_response");
        public static final StreamCodec<FriendlyByteBuf, ConfigResponseC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ConfigResponseC2SPacket::protocolVersion,
            ByteBufCodecs.BOOL, ConfigResponseC2SPacket::success,
            ConfigResponseC2SPacket::new
        );

        @Override
        public @NotNull Type<ConfigResponseC2SPacket> type() {
            return TYPE;
        }
    }

    public record ConfigSyncS2CPacket(int protocolVersion,
                                      LimitedModConfigServer applicableConfig,
                                      boolean isLimited,
                                      ModConfigServer fullConfig
    ) implements LeafCustomPayload {
        public static final Type<ConfigSyncS2CPacket> TYPE = createType("config_sync");
        public static final StreamCodec<FriendlyByteBuf, ConfigSyncS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ConfigSyncS2CPacket::protocolVersion,
            LimitedModConfigServer.getCodec(), ConfigSyncS2CPacket::applicableConfig,
            ByteBufCodecs.BOOL, ConfigSyncS2CPacket::isLimited,
            ModConfigServer.PACKET_CODEC, ConfigSyncS2CPacket::fullConfig,
            ConfigSyncS2CPacket::new
        );

        @Override
        public @NotNull Type<ConfigSyncS2CPacket> type() {
            return TYPE;
        }
    }

    public record ConfigUpdateAckS2CPacket(int protocolVersion, boolean success) implements LeafCustomPayload {
        public static final Type<ConfigUpdateAckS2CPacket> TYPE = createType("config_update_ack");
        public static final StreamCodec<FriendlyByteBuf, ConfigUpdateAckS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ConfigUpdateAckS2CPacket::protocolVersion,
            ByteBufCodecs.BOOL, ConfigUpdateAckS2CPacket::success,
            ConfigUpdateAckS2CPacket::new
        );

        @Override
        public @NotNull Type<ConfigUpdateAckS2CPacket> type() {
            return TYPE;
        }
    }

    public record ConfigUpdateC2SPacket(int protocolVersion, ModConfigServer config) implements LeafCustomPayload {
        public static final Type<ConfigUpdateC2SPacket> TYPE = createType("config_update");
        public static final StreamCodec<FriendlyByteBuf, ConfigUpdateC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ConfigUpdateC2SPacket::protocolVersion,
            ModConfigServer.PACKET_CODEC, ConfigUpdateC2SPacket::config,
            ConfigUpdateC2SPacket::new
        );

        @Override
        public @NotNull Type<ConfigUpdateC2SPacket> type() {
            return TYPE;
        }
    }

    public record RollSyncC2SPacket(boolean rolling, float roll) implements LeafCustomPayload {
        public static final Type<RollSyncC2SPacket> TYPE = createType("roll_sync");
        public static final StreamCodec<FriendlyByteBuf, RollSyncC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RollSyncC2SPacket::rolling,
            ByteBufCodecs.FLOAT, RollSyncC2SPacket::roll,
            RollSyncC2SPacket::new
        );

        @Override
        public @NotNull Type<RollSyncC2SPacket> type() {
            return TYPE;
        }
    }

    public record RollSyncS2CPacket(int entityId, boolean rolling, float roll) implements LeafCustomPayload {
        public static final Type<RollSyncS2CPacket> TYPE = createType("roll_sync");
        public static final StreamCodec<FriendlyByteBuf, RollSyncS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RollSyncS2CPacket::entityId,
            ByteBufCodecs.BOOL, RollSyncS2CPacket::rolling,
            ByteBufCodecs.FLOAT, RollSyncS2CPacket::roll,
            RollSyncS2CPacket::new
        );

        @Override
        public @NotNull Type<RollSyncS2CPacket> type() {
            return TYPE;
        }
    }

    public interface LimitedModConfigServer {
        boolean allowThrusting();

        boolean forceEnabled();

        static StreamCodec<ByteBuf, LimitedModConfigServer> getCodec() {
            return StreamCodec.composite(
                ByteBufCodecs.BOOL, LimitedModConfigServer::allowThrusting,
                ByteBufCodecs.BOOL, LimitedModConfigServer::forceEnabled,
                Impl::new
            );
        }

        record Impl(boolean allowThrusting, boolean forceEnabled) implements LimitedModConfigServer {
        }
    }

    public record ModConfigServer(boolean allowThrusting,
                                  boolean forceEnabled,
                                  boolean forceInstalled,
                                  int installedTimeout,
                                  KineticDamage kineticDamage
    ) implements LimitedModConfigServer {
        public static final StreamCodec<ByteBuf, ModConfigServer> PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ModConfigServer::allowThrusting,
            ByteBufCodecs.BOOL, ModConfigServer::forceEnabled,
            ByteBufCodecs.BOOL, ModConfigServer::forceInstalled,
            ByteBufCodecs.INT, ModConfigServer::installedTimeout,
            KineticDamage.CODEC, ModConfigServer::kineticDamage,
            ModConfigServer::new
        );
    }

    public enum KineticDamage {
        VANILLA,
        HIGH_SPEED,
        NONE,
        INSTANT_KILL;

        public static final StreamCodec<ByteBuf, KineticDamage> CODEC =
            ByteBufCodecs.STRING_UTF8.map(KineticDamage::valueOf, KineticDamage::name);
    }
}
