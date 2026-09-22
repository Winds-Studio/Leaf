package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.UUID;

public class PlayerIdentifier {

    public static final UUID MISSING_PLAYER_UUID = UUID.fromString("4c1b738f-56fa-4011-8273-498c972424ea");
    public static final PlayerIdentifier MISSING_PLAYER = new PlayerIdentifier(MISSING_PLAYER_UUID, "No Player");

    public final UUID uuid;
    private String bufferedPlayerName;

    PlayerIdentifier(final UUID uuid, final String bufferedPlayerName) {
        this.uuid = uuid;
        this.bufferedPlayerName = bufferedPlayerName;
    }

    public String getName() {
        return bufferedPlayerName;
    }

    public void updatePlayerName(final String name) {
        bufferedPlayerName = name;
    }

    public JsonObject toJson() {
        final JsonObject jsonObject = new JsonObject();

        jsonObject.add("uuid", new JsonPrimitive(uuid.toString()));
        jsonObject.add("name", new JsonPrimitive(bufferedPlayerName));

        return jsonObject;
    }
}
