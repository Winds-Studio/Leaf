package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public record EntityCollisionCache(
    ObjectArrayList<VoxelShape> potentialCollisionsVoxel,
    ObjectArrayList<AABB> potentialCollisionsBB,
    ObjectArrayList<AABB> entityAABBs
) {
    public EntityCollisionCache() {
        this(new ObjectArrayList<>(), new ObjectArrayList<>(), new ObjectArrayList<>());
    }

    public void clear() {
        potentialCollisionsVoxel.clear();
        potentialCollisionsBB.clear();
        entityAABBs.clear();
    }
}
