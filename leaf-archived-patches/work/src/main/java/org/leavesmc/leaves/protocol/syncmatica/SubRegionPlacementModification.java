package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubRegionPlacementModification {

    public final String name;
    public final BlockPos position;
    public final Rotation rotation;
    public final Mirror mirror;

    SubRegionPlacementModification(final String name, final BlockPos position, final Rotation rotation, final Mirror mirror) {
        this.name = name;
        this.position = position;
        this.rotation = rotation;
        this.mirror = mirror;
    }

    @Nullable
    public static SubRegionPlacementModification fromJson(final @NotNull JsonObject obj) {
        if (!obj.has("name") || !obj.has("position") || !obj.has("rotation") || !obj.has("mirror")) {
            return null;
        }

        final String name = obj.get("name").getAsString();
        final JsonArray arr = obj.get("position").getAsJsonArray();
        if (arr.size() != 3) {
            return null;
        }

        final BlockPos position = new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
        final Rotation rotation = Rotation.valueOf(obj.get("rotation").getAsString());
        final Mirror mirror = Mirror.valueOf(obj.get("mirror").getAsString());

        return new SubRegionPlacementModification(name, position, rotation, mirror);
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();

        final JsonArray arr = new JsonArray();
        arr.add(position.getX());
        arr.add(position.getY());
        arr.add(position.getZ());
        obj.add("position", arr);

        obj.add("name", new JsonPrimitive(name));
        obj.add("rotation", new JsonPrimitive(rotation.name()));
        obj.add("mirror", new JsonPrimitive(mirror.name()));

        return obj;
    }

    @Override
    public String toString() {
        return String.format("[name=%s, position=%s, rotation=%s, mirror=%s]", name, position, rotation, mirror);
    }
}
