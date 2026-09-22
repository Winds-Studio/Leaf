package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ServerPlacement {

    private final UUID id;

    private final String fileName;
    private final UUID hashValue;

    private PlayerIdentifier owner;
    private PlayerIdentifier lastModifiedBy;

    private ServerPosition origin;
    private Rotation rotation;
    private Mirror mirror;

    private SubRegionData subRegionData = new SubRegionData();

    public ServerPlacement(final UUID id, final String fileName, final UUID hashValue, final PlayerIdentifier owner) {
        this.id = id;
        this.fileName = removeExtension(fileName);
        this.hashValue = hashValue;
        this.owner = owner;
        lastModifiedBy = owner;
    }

    private static String removeExtension(final String fileName) {
        final int pos = fileName.lastIndexOf(".");
        if (pos < 0) {
            return fileName;
        }
        return fileName.substring(0, pos);
    }

    @Nullable
    public static ServerPlacement fromJson(final @NotNull JsonObject obj) {
        if (obj.has("id")
            && obj.has("file_name")
            && obj.has("hash")
            && obj.has("origin")
            && obj.has("rotation")
            && obj.has("mirror")) {
            final UUID id = UUID.fromString(obj.get("id").getAsString());
            final String name = obj.get("file_name").getAsString();
            final UUID hashValue = UUID.fromString(obj.get("hash").getAsString());

            PlayerIdentifier owner = PlayerIdentifier.MISSING_PLAYER;
            if (obj.has("owner")) {
                owner = SyncmaticaProtocol.getPlayerIdentifierProvider().fromJson(obj.get("owner").getAsJsonObject());
            }

            final ServerPlacement newPlacement = new ServerPlacement(id, name, hashValue, owner);
            final ServerPosition pos = ServerPosition.fromJson(obj.get("origin").getAsJsonObject());
            if (pos == null) {
                return null;
            }
            newPlacement.origin = pos;
            newPlacement.rotation = Rotation.valueOf(obj.get("rotation").getAsString());
            newPlacement.mirror = Mirror.valueOf(obj.get("mirror").getAsString());

            if (obj.has("lastModifiedBy")) {
                newPlacement.lastModifiedBy = SyncmaticaProtocol.getPlayerIdentifierProvider()
                    .fromJson(obj.get("lastModifiedBy").getAsJsonObject());
            } else {
                newPlacement.lastModifiedBy = owner;
            }

            if (obj.has("subregionData")) {
                newPlacement.subRegionData = SubRegionData.fromJson(obj.get("subregionData"));
            }

            return newPlacement;
        }

        return null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return fileName;
    }

    public UUID getHash() {
        return hashValue;
    }

    public String getDimension() {
        return origin.getDimensionId();
    }

    public BlockPos getPosition() {
        return origin.getBlockPosition();
    }

    public ServerPosition getOrigin() {
        return origin;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public Mirror getMirror() {
        return mirror;
    }

    public ServerPlacement move(final String dimensionId, final BlockPos origin, final Rotation rotation, final Mirror mirror) {
        move(new ServerPosition(origin, dimensionId), rotation, mirror);
        return this;
    }

    public ServerPlacement move(final ServerPosition origin, final Rotation rotation, final Mirror mirror) {
        this.origin = origin;
        this.rotation = rotation;
        this.mirror = mirror;
        return this;
    }

    public PlayerIdentifier getOwner() {
        return owner;
    }

    public void setOwner(final PlayerIdentifier playerIdentifier) {
        owner = playerIdentifier;
    }

    public PlayerIdentifier getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(final PlayerIdentifier lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public SubRegionData getSubRegionData() {
        return subRegionData;
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();
        obj.add("id", new JsonPrimitive(id.toString()));

        obj.add("file_name", new JsonPrimitive(fileName));
        obj.add("hash", new JsonPrimitive(hashValue.toString()));

        obj.add("origin", origin.toJson());
        obj.add("rotation", new JsonPrimitive(rotation.name()));
        obj.add("mirror", new JsonPrimitive(mirror.name()));

        obj.add("owner", owner.toJson());
        if (!owner.equals(lastModifiedBy)) {
            obj.add("lastModifiedBy", lastModifiedBy.toJson());
        }

        if (subRegionData.isModified()) {
            obj.add("subregionData", subRegionData.toJson());
        }

        return obj;
    }
}
