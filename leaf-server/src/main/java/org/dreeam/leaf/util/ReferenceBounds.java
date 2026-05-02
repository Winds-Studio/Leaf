package org.dreeam.leaf.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record ReferenceBounds(int minX, int maxX, int minZ, int maxZ) {
    public static ReferenceBounds around(BlockPos startPos) {
        int startChunkX = net.minecraft.core.SectionPos.blockToSectionCoord(startPos.getX());
        int startChunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(startPos.getZ());
        int radius = 8;

        return new ReferenceBounds(
            net.minecraft.core.SectionPos.sectionToBlockCoord(startChunkX - radius),
            net.minecraft.core.SectionPos.sectionToBlockCoord(startChunkX + radius, 15),
            net.minecraft.core.SectionPos.sectionToBlockCoord(startChunkZ - radius),
            net.minecraft.core.SectionPos.sectionToBlockCoord(startChunkZ + radius, 15)
        );
    }

    public boolean contains(BoundingBox box) {
        return box.minX() >= this.minX
            && box.maxX() <= this.maxX
            && box.minZ() >= this.minZ
            && box.maxZ() <= this.maxZ;
    }
}
