package org.dreeam.leaf.world.block;

import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class BatchedHeightMap {

    /**
     * Updates multiple heightmaps simultaneously instead of 4 separate calls
     */
    public static void batchUpdate(Heightmap motionBlocking, Heightmap motionBlockingNoLeaves, Heightmap oceanFloor, Heightmap worldSurface, LevelChunk chunk, int x, int y, int z, BlockState state) {

        // Cache predicates upfront for all heightmap types
        Predicate<BlockState> motionBlockingTest = Heightmap.Types.MOTION_BLOCKING.isOpaque();
        Predicate<BlockState> noLeavesTest = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque();
        Predicate<BlockState> oceanFloorTest = Heightmap.Types.OCEAN_FLOOR.isOpaque();
        Predicate<BlockState> worldSurfaceTest = Heightmap.Types.WORLD_SURFACE.isOpaque();

        int remainingMaps = filterActiveHeightmaps(motionBlocking, motionBlockingNoLeaves, oceanFloor, worldSurface, x, z, y);

        if (remainingMaps == 0) {
            return;
        }

        // Store references that may be modified
        Heightmap[] heightmaps = {motionBlocking, motionBlockingNoLeaves, oceanFloor, worldSurface};
        int[] currentHeights = {
            motionBlocking != null ? motionBlocking.getFirstAvailable(x, z) : 0,
            motionBlockingNoLeaves != null ? motionBlockingNoLeaves.getFirstAvailable(x, z) : 0,
            oceanFloor != null ? oceanFloor.getFirstAvailable(x, z) : 0,
            worldSurface != null ? worldSurface.getFirstAvailable(x, z) : 0
        };

        remainingMaps = processCurrentPosition(heightmaps, motionBlockingTest, noLeavesTest, oceanFloorTest, worldSurfaceTest, currentHeights, x, y, z, state, remainingMaps);

        if (remainingMaps > 0) {
            searchDownwardForValidBlocks(heightmaps, motionBlockingTest, noLeavesTest, oceanFloorTest, worldSurfaceTest, chunk, x, y, z, remainingMaps);
        }
    }

    private static int filterActiveHeightmaps(Heightmap motionBlocking, Heightmap motionBlockingNoLeaves, Heightmap oceanFloor, Heightmap worldSurface, int x, int z, int y) {
        int count = 0;

        if (motionBlocking != null && y + 2 > motionBlocking.getFirstAvailable(x, z)) {
            count++;
        }
        if (motionBlockingNoLeaves != null && y + 2 > motionBlockingNoLeaves.getFirstAvailable(x, z)) {
            count++;
        }
        if (oceanFloor != null && y + 2 > oceanFloor.getFirstAvailable(x, z)) {
            count++;
        }
        if (worldSurface != null && y + 2 > worldSurface.getFirstAvailable(x, z)) {
            count++;
        }

        return count;
    }

    private static int processCurrentPosition(Heightmap[] heightmaps, Predicate<BlockState> motionBlockingTest, Predicate<BlockState> noLeavesTest, Predicate<BlockState> oceanFloorTest, Predicate<BlockState> worldSurfaceTest, int[] heights, int x, int y, int z, BlockState state, int remaining) {

        // Process motion blocking
        if (heightmaps[0] != null) {
            if (motionBlockingTest.test(state)) {
                if (y >= heights[0]) {
                    heightmaps[0].setHeight(x, z, y + 1);
                }
                heightmaps[0] = null;
                remaining--;
            } else if (heights[0] != y + 1) {
                heightmaps[0] = null;
                remaining--;
            }
        }

        // Process motion blocking no leaves
        if (heightmaps[1] != null) {
            if (noLeavesTest.test(state)) {
                if (y >= heights[1]) {
                    heightmaps[1].setHeight(x, z, y + 1);
                }
                heightmaps[1] = null;
                remaining--;
            } else if (heights[1] != y + 1) {
                heightmaps[1] = null;
                remaining--;
            }
        }

        // Process ocean floor
        if (heightmaps[2] != null) {
            if (oceanFloorTest.test(state)) {
                if (y >= heights[2]) {
                    heightmaps[2].setHeight(x, z, y + 1);
                }
                heightmaps[2] = null;
                remaining--;
            } else if (heights[2] != y + 1) {
                heightmaps[2] = null;
                remaining--;
            }
        }

        // Process world surface
        if (heightmaps[3] != null) {
            if (worldSurfaceTest.test(state)) {
                if (y >= heights[3]) {
                    heightmaps[3].setHeight(x, z, y + 1);
                }
                heightmaps[3] = null;
                remaining--;
            } else if (heights[3] != y + 1) {
                heightmaps[3] = null;
                remaining--;
            }
        }

        return remaining;
    }

    private static void searchDownwardForValidBlocks(Heightmap[] heightmaps, Predicate<BlockState> motionBlockingTest, Predicate<BlockState> noLeavesTest, Predicate<BlockState> oceanFloorTest, Predicate<BlockState> worldSurfaceTest, LevelChunk chunk, int x, int y, int z, int activeCount) {
        int minY = chunk.getMinY();

        for (int searchY = y - 1; searchY >= minY && activeCount > 0; searchY--) {
            BlockState blockAtY = chunk.getBlockState(x, searchY, z);

            if (heightmaps[0] != null && motionBlockingTest.test(blockAtY)) {
                heightmaps[0].setHeight(x, z, searchY + 1);
                heightmaps[0] = null;
                activeCount--;
            }
            if (heightmaps[1] != null && noLeavesTest.test(blockAtY)) {
                heightmaps[1].setHeight(x, z, searchY + 1);
                heightmaps[1] = null;
                activeCount--;
            }
            if (heightmaps[2] != null && oceanFloorTest.test(blockAtY)) {
                heightmaps[2].setHeight(x, z, searchY + 1);
                heightmaps[2] = null;
                activeCount--;
            }
            if (heightmaps[3] != null && worldSurfaceTest.test(blockAtY)) {
                heightmaps[3].setHeight(x, z, searchY + 1);
                heightmaps[3] = null;
                activeCount--;
            }
        }

        // Handle any remaining heightmaps by setting them to minimum Y
        finalizeRemainingHeightmaps(heightmaps, x, z, minY);
    }

    private static void finalizeRemainingHeightmaps(Heightmap[] heightmaps, int x, int z, int minY) {
        for (Heightmap heightmap : heightmaps) {
            if (heightmap != null) {
                heightmap.setHeight(x, z, minY);
            }
        }
    }
}
