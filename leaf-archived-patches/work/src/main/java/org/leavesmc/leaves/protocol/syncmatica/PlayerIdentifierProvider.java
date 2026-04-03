package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.protocol.syncmatica.exchange.ExchangeTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerIdentifierProvider {

    private final Map<UUID, PlayerIdentifier> identifiers = new HashMap<>();

    public PlayerIdentifierProvider() {
        identifiers.put(PlayerIdentifier.MISSING_PLAYER_UUID, PlayerIdentifier.MISSING_PLAYER);
    }

    public PlayerIdentifier createOrGet(final ExchangeTarget exchangeTarget) {
        return createOrGet(CommunicationManager.getGameProfile(exchangeTarget));
    }

    public PlayerIdentifier createOrGet(final @NotNull GameProfile gameProfile) {
        return createOrGet(gameProfile.getId(), gameProfile.getName());
    }

    public PlayerIdentifier createOrGet(final UUID uuid, final String playerName) {
        return identifiers.computeIfAbsent(uuid, id -> new PlayerIdentifier(uuid, playerName));
    }

    public void updateName(final UUID uuid, final String playerName) {
        createOrGet(uuid, playerName).updatePlayerName(playerName);
    }

    public PlayerIdentifier fromJson(final @NotNull JsonObject obj) {
        if (!obj.has("uuid") || !obj.has("name")) {
            return PlayerIdentifier.MISSING_PLAYER;
        }

        final UUID jsonUUID = UUID.fromString(obj.get("uuid").getAsString());
        return identifiers.computeIfAbsent(jsonUUID,
            key -> new PlayerIdentifier(jsonUUID, obj.get("name").getAsString())
        );
    }
}
