package org.dreeam.leaf.world.block;

/*
    From Lithium https://github.com/CaffeineMC/lithium/blob/develop/common/src/main/java/net/caffeinemc/mods/lithium/common/world/chunk/heightmap/CombinedHeightmapUpdate.java
 */

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class CombinedHeightmapUpdate {
    public static void updateHeightmaps(Heightmap heightmap0, Heightmap heightmap1, Heightmap heightmap2, Heightmap heightmap3, LevelChunk worldChunk, int x, int y, int z, BlockState state) {
        Predicate<BlockState> blockPredicate3;
        int height0 = heightmap0.getFirstAvailable(x, z);
        int height1 = heightmap1.getFirstAvailable(x, z);
        int height2 = heightmap2.getFirstAvailable(x, z);
        int height3 = heightmap3.getFirstAvailable(x, z);
        int heightmapsToUpdate = 4;
        if (y + 2 <= height0) {
            heightmap0 = null;
            --heightmapsToUpdate;
        }
        if (y + 2 <= height1) {
            heightmap1 = null;
            --heightmapsToUpdate;
        }
        if (y + 2 <= height2) {
            heightmap2 = null;
            --heightmapsToUpdate;
        }
        if (y + 2 <= height3) {
            heightmap3 = null;
            --heightmapsToUpdate;
        }
        if (heightmapsToUpdate == 0) {
            return;
        }
        Predicate<BlockState> blockPredicate0 = heightmap0 == null ? null : Objects.requireNonNull(heightmap0.isOpaque());
        Predicate<BlockState> blockPredicate1 = heightmap1 == null ? null : Objects.requireNonNull(heightmap1.isOpaque());
        Predicate<BlockState> blockPredicate2 = heightmap2 == null ? null : Objects.requireNonNull(heightmap2.isOpaque());
        Predicate<BlockState> predicate = blockPredicate3 = heightmap3 == null ? null : Objects.requireNonNull(heightmap3.isOpaque());
        if (heightmap0 != null) {
            if (blockPredicate0.test(state)) {
                if (y >= height0) {
                    heightmap0.setHeight(x, z, y + 1);
                }
                heightmap0 = null;
                --heightmapsToUpdate;
            } else if (height0 != y + 1) {
                heightmap0 = null;
                --heightmapsToUpdate;
            }
        }
        if (heightmap1 != null) {
            if (blockPredicate1.test(state)) {
                if (y >= height1) {
                    heightmap1.setHeight(x, z, y + 1);
                }
                heightmap1 = null;
                --heightmapsToUpdate;
            } else if (height1 != y + 1) {
                heightmap1 = null;
                --heightmapsToUpdate;
            }
        }
        if (heightmap2 != null) {
            if (blockPredicate2.test(state)) {
                if (y >= height2) {
                    heightmap2.setHeight(x, z, y + 1);
                }
                heightmap2 = null;
                --heightmapsToUpdate;
            } else if (height2 != y + 1) {
                heightmap2 = null;
                --heightmapsToUpdate;
            }
        }
        if (heightmap3 != null) {
            if (blockPredicate3.test(state)) {
                if (y >= height3) {
                    heightmap3.setHeight(x, z, y + 1);
                }
                heightmap3 = null;
                --heightmapsToUpdate;
            } else if (height3 != y + 1) {
                heightmap3 = null;
                --heightmapsToUpdate;
            }
        }
        if (heightmapsToUpdate == 0) {
            return;
        }
        int bottomY = worldChunk.getMinY();
        for (int searchY = y - 1; searchY >= bottomY && heightmapsToUpdate > 0; --searchY) {
            BlockState blockState = worldChunk.getBlockState(x, searchY, z);
            if (heightmap0 != null && blockPredicate0.test(blockState)) {
                heightmap0.setHeight(x, z, searchY + 1);
                heightmap0 = null;
                --heightmapsToUpdate;
            }
            if (heightmap1 != null && blockPredicate1.test(blockState)) {
                heightmap1.setHeight(x, z, searchY + 1);
                heightmap1 = null;
                --heightmapsToUpdate;
            }
            if (heightmap2 != null && blockPredicate2.test(blockState)) {
                heightmap2.setHeight(x, z, searchY + 1);
                heightmap2 = null;
                --heightmapsToUpdate;
            }
            if (heightmap3 == null || !blockPredicate3.test(blockState)) continue;
            heightmap3.setHeight(x, z, searchY + 1);
            heightmap3 = null;
            --heightmapsToUpdate;
        }
        if (heightmap0 != null) {
            heightmap0.setHeight(x, z, bottomY);
        }
        if (heightmap1 != null) {
            heightmap1.setHeight(x, z, bottomY);
        }
        if (heightmap2 != null) {
            heightmap2.setHeight(x, z, bottomY);
        }
        if (heightmap3 != null) {
            heightmap3.setHeight(x, z, bottomY);
        }
    }
}
