package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SyncmaticManager {

    public static final String PLACEMENTS_JSON_KEY = "placements";
    private final Map<UUID, ServerPlacement> schematics = new HashMap<>();

    public void addPlacement(final ServerPlacement placement) {
        schematics.put(placement.getId(), placement);
        updateServerPlacement();
    }

    public ServerPlacement getPlacement(final UUID id) {
        return schematics.get(id);
    }

    public Collection<ServerPlacement> getAll() {
        return schematics.values();
    }

    public void removePlacement(final @NotNull ServerPlacement placement) {
        schematics.remove(placement.getId());
        updateServerPlacement();
    }

    public void updateServerPlacement() {
        saveServer();
    }

    public void startup() {
        loadServer();
    }

    private void saveServer() {
        final JsonObject obj = new JsonObject();
        final JsonArray arr = new JsonArray();

        for (final ServerPlacement p : getAll()) {
            arr.add(p.toJson());
        }

        obj.add(PLACEMENTS_JSON_KEY, arr);
        final File backup = new File(SyncmaticaProtocol.getLitematicFolder(), "placements.json.bak");
        final File incoming = new File(SyncmaticaProtocol.getLitematicFolder(), "placements.json.new");
        final File current = new File(SyncmaticaProtocol.getLitematicFolder(), "placements.json");

        try (final FileWriter writer = new FileWriter(incoming)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }

        SyncmaticaProtocol.backupAndReplace(backup.toPath(), current.toPath(), incoming.toPath());
    }

    private void loadServer() {
        final File f = new File(SyncmaticaProtocol.getLitematicFolder(), "placements.json");
        if (f.exists() && f.isFile() && f.canRead()) {
            JsonElement element = null;
            try {
                final JsonParser parser = new JsonParser();
                final FileReader reader = new FileReader(f);

                element = parser.parse(reader);
                reader.close();

            } catch (final Exception e) {
                e.printStackTrace();
            }
            if (element == null) {
                return;
            }
            try {
                final JsonObject obj = element.getAsJsonObject();
                if (obj == null || !obj.has(PLACEMENTS_JSON_KEY)) {
                    return;
                }
                final JsonArray arr = obj.getAsJsonArray(PLACEMENTS_JSON_KEY);
                for (final JsonElement elem : arr) {
                    final ServerPlacement placement = ServerPlacement.fromJson(elem.getAsJsonObject());
                    if (placement != null) {
                        schematics.put(placement.getId(), placement);
                    }
                }

            } catch (final IllegalStateException | NullPointerException e) {
                e.printStackTrace();
            }
        }
    }
}
