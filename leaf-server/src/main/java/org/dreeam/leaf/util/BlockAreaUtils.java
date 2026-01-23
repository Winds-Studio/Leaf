package org.dreeam.leaf.util;

import net.minecraft.core.BlockPos;

public final class BlockAreaUtils {
    private BlockAreaUtils() {}

    public static BlockPos[] getBlocksBetween(BlockPos start, BlockPos end) {
        return generateBlockPosArray(
            Math.min(start.getX(), end.getX()),
            Math.min(start.getY(), end.getY()),
            Math.min(start.getZ(), end.getZ()),
            Math.max(start.getX(), end.getX()),
            Math.max(start.getY(), end.getY()),
            Math.max(start.getZ(), end.getZ())
        );
    }

    private static BlockPos[] generateBlockPosArray(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        int size = width * height * depth;
        BlockPos[] result = new BlockPos[size];

        int index = 0;
        for (int y = 0; y < height; y++) {
            int currentY = minY + y;
            for (int z = 0; z < depth; z++) {
                int currentZ = minZ + z;
                BlockPos[] xChunk = new BlockPos[width];
                for (int x = 0; x < width; x++) {
                    xChunk[x] = new BlockPos(minX + x, currentY, currentZ);
                }
                System.arraycopy(xChunk, 0, result, index, width);
                index += width;
            }
        }
        return result;
    }
}
