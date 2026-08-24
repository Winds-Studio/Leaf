package net.caffeinemc.mods.lithium.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class Distances {

    public static long getMinChunkToBlockDistanceL2Sq(BlockPos origin, int chunkX, int chunkZ) {
        int chunkMinX = SectionPos.sectionToBlockCoord(chunkX);
        int chunkMinZ = SectionPos.sectionToBlockCoord(chunkZ);

        int xDistance = origin.getX() - chunkMinX;
        if (xDistance > 0) {
            xDistance = Math.max(0, xDistance - 15);
        }
        int zDistance = origin.getZ() - chunkMinZ;
        if (zDistance > 0) {
            zDistance = Math.max(0, zDistance - 15);
        }

        return (long) xDistance * (long) xDistance + (long) zDistance * (long) zDistance;
    }

    public static BlockPos getClosestPosInChunk(BlockPos origin, int chunkX, int chunkZ) {
        int closestX = getClosestBlockCoordInSection(origin.getX(), chunkX);
        int closestZ = getClosestBlockCoordInSection(origin.getZ(), chunkZ);
        return new BlockPos(closestX, origin.getY(), closestZ);
    }

    public static boolean isWithinCubeRadius(BlockPos origin, int radius, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= radius &&
                Math.abs(pos.getZ() - origin.getZ()) <= radius;
    }

    public static boolean isWithinSphereRadius(BlockPos origin, long radiusSq, BlockPos pos) {
        return distanceSq(origin, pos) <= radiusSq;
    }

    public static int getClosestBlockCoordInSection(int blockCoord, int sectionCoord) {
        final int minBlockInSection = SectionPos.sectionToBlockCoord(sectionCoord);
        return Math.min(Math.max(blockCoord, minBlockInSection), minBlockInSection + 15);
    }

    public static long getMinSectionDistanceSq(BlockPos origin, int chunkX, int chunkY, int chunkZ) {
        int originX = origin.getX(), originY = origin.getY(), originZ = origin.getZ();
        long distX = getClosestBlockCoordInSection(originX, chunkX) - originX;
        long distY = getClosestBlockCoordInSection(originY, chunkY) - originY;
        long distZ = getClosestBlockCoordInSection(originZ, chunkZ) - originZ;

        return distX * distX + distY * distY + distZ * distZ;
    }

    public static long distanceSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static int distanceSqInt(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        // Check overflows to avoid silent incorrect results
        return Math.addExact(Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)), Math.multiplyExact(dz, dz));
    }
}
