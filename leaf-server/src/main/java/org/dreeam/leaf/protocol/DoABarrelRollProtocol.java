package org.dreeam.leaf.protocol;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.ConfigResponseC2SPacket;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.ConfigSyncS2CPacket;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.ConfigUpdateAckS2CPacket;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.ConfigUpdateC2SPacket;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.KineticDamage;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.ModConfigServer;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.RollSyncC2SPacket;
import org.dreeam.leaf.protocol.DoABarrelRollPackets.RollSyncS2CPacket;
import org.bukkit.event.player.PlayerKickEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

public class DoABarrelRollProtocol implements Protocol {

    protected static final String NAMESPACE = "do_a_barrel_roll";
    private static final Logger LOGGER = LogManager.getLogger(NAMESPACE);
    private static final int PROTOCOL_VERSION = 4;
    private static final ModConfigServer DEFAULT = new ModConfigServer(false, false, false, 40, KineticDamage.VANILLA);
    private static final Component SYNC_TIMEOUT_MESSAGE = Component.literal("Please install Do a Barrel Roll 2.4.0 or later to play on this server.");
    @Nullable
    private static DoABarrelRollProtocol INSTANCE = null;

    private final List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> c2s = ImmutableList.of(
        new Protocols.TypeAndCodec<>(ConfigUpdateC2SPacket.TYPE, ConfigUpdateC2SPacket.STREAM_CODEC),
        new Protocols.TypeAndCodec<>(ConfigResponseC2SPacket.TYPE, ConfigResponseC2SPacket.STREAM_CODEC),
        new Protocols.TypeAndCodec<>(RollSyncC2SPacket.TYPE, RollSyncC2SPacket.STREAM_CODEC));

    private final List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> s2c = ImmutableList.of(
        new Protocols.TypeAndCodec<>(ConfigUpdateAckS2CPacket.TYPE, ConfigUpdateAckS2CPacket.STREAM_CODEC),
        new Protocols.TypeAndCodec<>(ConfigSyncS2CPacket.TYPE, ConfigSyncS2CPacket.STREAM_CODEC),
        new Protocols.TypeAndCodec<>(RollSyncS2CPacket.TYPE, RollSyncS2CPacket.STREAM_CODEC)
    );

    private ModConfigServer config = DEFAULT;
    private boolean configUpdated = false;

    private final Reference2ReferenceMap<ServerGamePacketListenerImpl, ClientInfo> syncStates = Reference2ReferenceMaps.synchronize(new Reference2ReferenceOpenHashMap<>());
    private final Reference2ReferenceMap<ServerGamePacketListenerImpl, DelayedRunnable> scheduledKicks = Reference2ReferenceMaps.synchronize(new Reference2ReferenceOpenHashMap<>());
    public final Reference2BooleanMap<ServerGamePacketListenerImpl> isRollingMap = Reference2BooleanMaps.synchronize(new Reference2BooleanOpenHashMap<>());
    public final Reference2FloatMap<ServerGamePacketListenerImpl> rollMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap<>());
    public final Reference2BooleanMap<ServerGamePacketListenerImpl> lastIsRollingMap = Reference2BooleanMaps.synchronize(new Reference2BooleanOpenHashMap<>());
    public final Reference2FloatMap<ServerGamePacketListenerImpl> lastRollMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap<>());

    public static void deinit() {
        DoABarrelRollProtocol instance = INSTANCE;
        if (instance != null) {
            INSTANCE = null;
            Protocols.unregister(instance);
        }
    }

    public static void init(
        boolean allowThrusting,
        boolean forceEnabled,
        boolean forceInstalled,
        int installedTimeout,
        KineticDamage kineticDamage
    ) {
        if (INSTANCE == null) {
            INSTANCE = new DoABarrelRollProtocol();
            Protocols.register(INSTANCE);
        }
        INSTANCE.config = new ModConfigServer(allowThrusting, forceEnabled, forceInstalled, installedTimeout, kineticDamage);
        INSTANCE.configUpdated = true;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> c2s() {
        return c2s;
    }

    @Override
    public List<Protocols.TypeAndCodec<FriendlyByteBuf, ? extends LeafCustomPayload>> s2c() {
        return s2c;
    }

    @Override
    public void handle(ServerPlayer player, LeafCustomPayload payload) {
        switch (payload) {
            case ConfigUpdateC2SPacket ignored ->
                player.connection.send(Protocols.createPacket(new ConfigUpdateAckS2CPacket(PROTOCOL_VERSION, false)));
            case ConfigResponseC2SPacket configResponseC2SPacket -> {
                var reply = clientReplied(player.connection, configResponseC2SPacket);
                if (reply == HandshakeState.RESEND) {
                    sendHandshake(player.connection);
                }
            }
            case RollSyncC2SPacket rollSyncC2SPacket -> {
                var state = getHandshakeState(player.connection);
                if (state.state != HandshakeState.ACCEPTED) {
                    return;
                }
                var rolling = rollSyncC2SPacket.rolling();
                var roll = rollSyncC2SPacket.roll();
                isRollingMap.put(player.connection, rolling);
                if (Float.isInfinite(roll)) {
                    roll = 0.0F;
                }
                rollMap.put(player.connection, roll);
            }
            default -> {
            }
        }
    }

    @Override
    public void disconnected(ServerPlayer player) {
        final var handler = player.connection;
        syncStates.remove(handler);
        isRollingMap.removeBoolean(handler);
        rollMap.removeFloat(handler);
        lastIsRollingMap.removeBoolean(handler);
        lastRollMap.removeFloat(handler);
    }

    @Override
    public void tickPlayer(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        if (getHandshakeState(connection).state == HandshakeState.NOT_SENT) {
            sendHandshake(connection);
        }
        if (!isRollingMap.containsKey(connection)) {
            return;
        }
        if (!isRollingMap.getBoolean(connection)) {
            rollMap.put(connection, 0.0F);
        }

        boolean isRolling = isRollingMap.getBoolean(connection);
        float roll = rollMap.getFloat(connection);
        boolean lastIsRolling = lastIsRollingMap.getBoolean(connection);
        float lastRoll = lastRollMap.getFloat(connection);
        if (isRolling == lastIsRolling && roll == lastRoll) {
            return;
        }
        var payload = new RollSyncS2CPacket(player.getId(), isRolling, roll);
        var packet = Protocols.createPacket(payload);
        var tracked = player.moonrise$getTrackedEntity();
        if (tracked == null) {
            return;
        }
        for (ServerPlayerConnection seenBy : tracked.seenBy()) {
            if (seenBy instanceof ServerGamePacketListenerImpl conn
                && getHandshakeState(conn).state == HandshakeState.ACCEPTED) {
                seenBy.send(packet);
            }
        }
        lastIsRollingMap.put(connection, isRolling);
        lastRollMap.put(connection, roll);
    }

    @Override
    public void tickServer(MinecraftServer server) {
        var it = scheduledKicks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().isDone()) {
                it.remove();
            } else {
                entry.getValue().tick();
            }
        }

        if (configUpdated) {
            configUpdated = false;
            for (ServerPlayer player : server.getPlayerList().players) {
                sendHandshake(player.connection);
            }
        }
    }

    private OptionalInt getSyncTimeout(ModConfigServer config) {
        return config.forceInstalled() ? OptionalInt.of(config.installedTimeout()) : OptionalInt.empty();
    }

    private void sendHandshake(ServerGamePacketListenerImpl connection) {
        connection.send(Protocols.createPacket(initiateConfigSync(connection)));
        configSentToClient(connection);
    }

    private void configSentToClient(ServerGamePacketListenerImpl handler) {
        getHandshakeState(handler).state = HandshakeState.SENT;

        OptionalInt timeout = getSyncTimeout(config);
        if (timeout.isEmpty()) {
            return;
        }
        scheduledKicks.put(handler, new DelayedRunnable(timeout.getAsInt(), () -> {
            if (getHandshakeState(handler).state != HandshakeState.ACCEPTED) {
                LOGGER.warn(
                    "{} did not accept config syncing, config indicates we kick them.",
                    handler.getPlayer().getName().getString()
                );
                handler.disconnect(SYNC_TIMEOUT_MESSAGE, PlayerKickEvent.Cause.PLUGIN);
            }
        }));
    }

    private HandshakeState clientReplied(ServerGamePacketListenerImpl handler, ConfigResponseC2SPacket packet) {
        var info = getHandshakeState(handler);
        var player = handler.getPlayer();

        if (info.state == HandshakeState.SENT) {
            var protocolVersion = packet.protocolVersion();
            if (protocolVersion < 1 || protocolVersion > PROTOCOL_VERSION) {
                LOGGER.warn(
                    "{} sent unknown protocol version, expected range 1-{}, got {}. Will attempt to proceed anyway.",
                    player.getName().getString(),
                    PROTOCOL_VERSION,
                    protocolVersion
                );
            }

            if (protocolVersion == 2 && info.protocolVersion != 2) {
                LOGGER.info("{} is using an older protocol version, resending.", player.getName().getString());
                info.state = HandshakeState.RESEND;
            } else if (packet.success()) {
                LOGGER.info("{} accepted server config.", player.getName().getString());
                info.state = HandshakeState.ACCEPTED;
            } else {
                LOGGER.warn(
                    "{} failed to process server config, check client logs find what went wrong.",
                    player.getName().getString());
                info.state = HandshakeState.FAILED;
            }
            info.protocolVersion = protocolVersion;
        }

        return info.state;
    }

    private boolean isLimited(ServerGamePacketListenerImpl ignore) {
        return true;
        // return net.getPlayer().getBukkitEntity().hasPermission(DoABarrelRoll.MODID + ".configure");
    }

    private ClientInfo getHandshakeState(ServerGamePacketListenerImpl handler) {
        return syncStates.computeIfAbsent(handler, key -> new ClientInfo(HandshakeState.NOT_SENT, PROTOCOL_VERSION, true));
    }

    private ConfigSyncS2CPacket initiateConfigSync(ServerGamePacketListenerImpl handler) {
        var isLimited = isLimited(handler);
        // getHandshakeState(handler).isLimited = isLimited;
        return new ConfigSyncS2CPacket(PROTOCOL_VERSION, config, isLimited, isLimited ? DEFAULT : config);
    }

    private static final class ClientInfo {
        private HandshakeState state;
        private int protocolVersion;
        // private boolean isLimited;

        private ClientInfo(HandshakeState state, int protocolVersion, boolean ignore) {
            this.state = state;
            this.protocolVersion = protocolVersion;
            // this.isLimited = isLimited;
        }
    }

    private static final class DelayedRunnable {
        private final Runnable runnable;
        private final int delay;
        private int ticks = 0;

        private DelayedRunnable(int delay, Runnable runnable) {
            this.runnable = runnable;
            this.delay = delay;
        }

        private void tick() {
            if (++ticks >= delay) {
                runnable.run();
            }
        }

        private boolean isDone() {
            return ticks >= delay;
        }
    }

    private enum HandshakeState {
        NOT_SENT,
        SENT,
        ACCEPTED,
        FAILED,
        RESEND
    }
}
