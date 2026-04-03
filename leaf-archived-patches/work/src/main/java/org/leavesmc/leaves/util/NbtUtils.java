package org.leavesmc.leaves.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NbtUtils {

    public static void writeBlockPosToTag(Vec3i pos, CompoundTag tag) {
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
    }

    @Nullable
    public static BlockPos readBlockPos(@Nullable CompoundTag tag) {
        if (tag != null && tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            return new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
        }

        return null;
    }

    public static void writeEntityPositionToTag(Vec3 pos, CompoundTag tag) {
        ListTag posList = new ListTag();

        posList.add(DoubleTag.valueOf(pos.x));
        posList.add(DoubleTag.valueOf(pos.y));
        posList.add(DoubleTag.valueOf(pos.z));
        tag.put("Pos", posList);
    }

    @Nullable
    public static Vec3 readVec3(@Nullable CompoundTag tag) {
        if (tag != null && tag.contains("dx") && tag.contains("dy") && tag.contains("dz")) {
            return new Vec3(tag.getDoubleOr("dx", 0.0), tag.getDoubleOr("dy", 0.0), tag.getDoubleOr("dz", 0.0));
        }
        return null;
    }

    @Nullable
    public static Vec3 readEntityPositionFromTag(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Pos")) {
            return null;
        }
        ListTag tagList = tag.getListOrEmpty("Pos");
        if (tagList.size() != 3) {
            return null;
        }
        return new Vec3(tagList.getDoubleOr(0, 0.0), tagList.getDoubleOr(1, 0.0), tagList.getDoubleOr(2, 0.0));
    }

    @Nullable
    public static Vec3i readVec3iFromTag(@Nullable CompoundTag tag) {
        if (tag != null && tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            return new Vec3i(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
        }
        return null;
    }

    public static BlockPos readBlockPosFromArrayTag(@NotNull CompoundTag tag, String tagName) {
        if (tag.contains(tagName)) {
            int[] pos = tag.getIntArray(tagName).orElse(new int[0]);
            if (pos.length == 3) {
                return new BlockPos(pos[0], pos[1], pos[2]);
            }
        }
        return null;
    }
}
