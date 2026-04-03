package org.leavesmc.leaves.protocol;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.food.FoodData;
import org.dreeam.leaf.config.modules.network.ProtocolSupport;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.core.Context;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@LeavesProtocol.Register(namespace = "asteorbar")
public class AsteorBarProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "asteorbar";

    private static final ResourceLocation NETWORK_KEY = id("network");

    private static final Map<UUID, Float> previousSaturationLevels = new HashMap<>();
    private static final Map<UUID, Float> previousExhaustionLevels = new HashMap<>();

    private static final float THRESHOLD = 0.01F;

    private static final Set<UUID> players = new HashSet<>();

    @Contract("_ -> new")
    public static @NotNull ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PROTOCOL_ID, path);
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerLoggedIn(@NotNull ServerPlayer player) {
        resetPlayerData(player);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLoggedOut(@NotNull ServerPlayer player) {
        players.remove(player.getUUID());
        resetPlayerData(player);
    }

    @ProtocolHandler.MinecraftRegister(onlyNamespace = true)
    public static void onPlayerSubscribed(@NotNull Context context, ResourceLocation id) {
        players.add(context.profile().getId());
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        final PlayerList playerList = MinecraftServer.getServer().getPlayerList();
        for (UUID uuid : players) {
            ServerPlayer player = playerList.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            FoodData data = player.getFoodData();

            float saturation = data.getSaturationLevel();
            Float previousSaturation = previousSaturationLevels.get(uuid);
            if (previousSaturation == null || saturation != previousSaturation) {
                ProtocolUtils.sendBytebufPacket(player, NETWORK_KEY, buf -> {
                    buf.writeByte(1);
                    buf.writeFloat(saturation);
                });
                previousSaturationLevels.put(uuid, saturation);
            }

            float exhaustion = data.exhaustionLevel;
            Float previousExhaustion = previousExhaustionLevels.get(uuid);
            if (previousExhaustion == null || Math.abs(exhaustion - previousExhaustion) >= THRESHOLD) {
                ProtocolUtils.sendBytebufPacket(player, NETWORK_KEY, buf -> {
                    buf.writeByte(0);
                    buf.writeFloat(exhaustion);
                });
                previousExhaustionLevels.put(uuid, exhaustion);
            }
        }
    }

    @ProtocolHandler.ReloadServer
    public static void onServerReload() {
        if (!org.dreeam.leaf.config.modules.network.ProtocolSupport.asteorBarProtocol) {
            disableAllPlayer();
        }
    }

    public static void disableAllPlayer() {
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            onPlayerLoggedOut(player);
        }
    }

    private static void resetPlayerData(@NotNull ServerPlayer player) {
        previousExhaustionLevels.remove(player.getUUID());
        previousSaturationLevels.remove(player.getUUID());
    }

    @Override
    public boolean isActive() {
        return ProtocolSupport.asteorBarProtocol;
    }
}
