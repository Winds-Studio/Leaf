package org.dreeam.leaf.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record ReferenceBounds(int minX, int maxX, int minZ, int maxZ) {
    public static ReferenceBounds around(BlockPos startPos) {
        int startChunkX = SectionPos.blockToSectionCoord(startPos.getX());
        int startChunkZ = SectionPos.blockToSectionCoord(startPos.getZ());
        int radius = 8; // ChunkStatus#MAX_STRUCTURE_DISTANCE

        return new ReferenceBounds(
            SectionPos.sectionToBlockCoord(startChunkX - radius),
            SectionPos.sectionToBlockCoord(startChunkX + radius, 15),
            SectionPos.sectionToBlockCoord(startChunkZ - radius),
            SectionPos.sectionToBlockCoord(startChunkZ + radius, 15)
        );
    }

    public boolean contains(BoundingBox box) {
        return box.minX() >= this.minX
            && box.maxX() <= this.maxX
            && box.minZ() >= this.minZ
            && box.maxZ() <= this.maxZ;
    }
}
