package org.leavesmc.leaves.protocol;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
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

@LeavesProtocol.Register(namespace = "appleskin")
public class AppleSkinProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "appleskin";

    private static final ResourceLocation SATURATION_KEY = id("saturation");
    private static final ResourceLocation EXHAUSTION_KEY = id("exhaustion");
    private static final ResourceLocation NATURAL_REGENERATION_KEY = id("natural_regeneration");

    private static final float MINIMUM_EXHAUSTION_CHANGE_THRESHOLD = 0.01F;

    private static final Map<ServerPlayer, Float> previousSaturationLevels = new HashMap<>();
    private static final Map<ServerPlayer, Float> previousExhaustionLevels = new HashMap<>();
    private static final Map<ServerPlayer, Boolean> previousNaturalRegeneration = new HashMap<>();

    private static final Map<UUID, Set<String>> subscribedChannels = new HashMap<>();

    @Contract("_ -> new")
    public static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(PROTOCOL_ID, path);
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerLoggedIn(@NotNull ServerPlayer player) {
        resetPlayerData(player);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLoggedOut(@NotNull ServerPlayer player) {
        subscribedChannels.remove(player.getUUID());
        resetPlayerData(player);
    }

    @ProtocolHandler.MinecraftRegister(onlyNamespace = true)
    public static void onPlayerSubscribed(@NotNull Context context, ResourceLocation id) {
        subscribedChannels.computeIfAbsent(context.profile().getId(), k -> new HashSet<>()).add(id.getPath());
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        final PlayerList playerList = MinecraftServer.getServer().getPlayerList(); // Leaf - optimize leaves protocol manager
        for (Map.Entry<UUID, Set<String>> entry : subscribedChannels.entrySet()) {
            ServerPlayer player = playerList.getPlayer(entry.getKey()); // Leaf - optimize leaves protocol manager
            if (player == null) {
                continue;
            }

            FoodData data = player.getFoodData();
            for (String channel : entry.getValue()) {
                switch (channel) {
                    case "saturation" -> {
                        float saturation = data.getSaturationLevel();
                        Float previousSaturation = previousSaturationLevels.get(player);
                        if (previousSaturation == null || saturation != previousSaturation) {
                            ProtocolUtils.sendBytebufPacket(player, SATURATION_KEY, buf -> buf.writeFloat(saturation));
                            previousSaturationLevels.put(player, saturation);
                        }
                    }

                    case "exhaustion" -> {
                        float exhaustion = data.exhaustionLevel;
                        Float previousExhaustion = previousExhaustionLevels.get(player);
                        if (previousExhaustion == null || Math.abs(exhaustion - previousExhaustion) >= MINIMUM_EXHAUSTION_CHANGE_THRESHOLD) {
                            ProtocolUtils.sendBytebufPacket(player, EXHAUSTION_KEY, buf -> buf.writeFloat(exhaustion));
                            previousExhaustionLevels.put(player, exhaustion);
                        }
                    }

                    case "natural_regeneration" -> {
                        boolean regeneration = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
                        Boolean previousRegeneration = previousNaturalRegeneration.get(player);
                        if (previousRegeneration == null || regeneration != previousRegeneration) {
                            ProtocolUtils.sendBytebufPacket(player, NATURAL_REGENERATION_KEY, buf -> buf.writeBoolean(regeneration));
                            previousNaturalRegeneration.put(player, regeneration);
                        }
                    }
                }
            }
        }
    }

    @ProtocolHandler.ReloadServer
    public static void onServerReload() {
        disableAllPlayer();
    }

    public static void disableAllPlayer() {
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            onPlayerLoggedOut(player);
        }
    }

    private static void resetPlayerData(@NotNull ServerPlayer player) {
        previousExhaustionLevels.remove(player);
        previousSaturationLevels.remove(player);
        previousNaturalRegeneration.remove(player);
    }

    @Override
    public int tickerInterval(String tickerID) {
        return ProtocolSupport.appleskinSyncTickInterval;
    }

    @Override
    public boolean isActive() {
        return ProtocolSupport.appleskinProtocol;
    }
}
