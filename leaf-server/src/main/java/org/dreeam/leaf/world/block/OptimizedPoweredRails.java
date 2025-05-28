package org.dreeam.leaf.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.HashMap;

import static net.minecraft.world.level.block.Block.*;
import static net.minecraft.world.level.block.PoweredRailBlock.POWERED;
import static net.minecraft.world.level.block.PoweredRailBlock.SHAPE;

public class OptimizedPoweredRails {

    private static final Direction[] EAST_WEST_DIR = new Direction[]{Direction.WEST, Direction.EAST};
    private static final Direction[] NORTH_SOUTH_DIR = new Direction[]{Direction.SOUTH, Direction.NORTH};

    private static final int UPDATE_FORCE_PLACE = UPDATE_MOVE_BY_PISTON | UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS;

    private static int RAIL_POWER_LIMIT = 8;

    private static void giveShapeUpdate(Level level, BlockState state, BlockPos pos, BlockPos fromPos, Direction direction) {
        BlockState oldState = level.getBlockState(pos);
        Block.updateOrDestroy(
            oldState,
            oldState.updateShape(level, level, pos, direction.getOpposite(), fromPos, state, level.random),
            level,
            pos,
            UPDATE_CLIENTS & -34,
            0
        );
    }

    public static int getRailPowerLimit() {
        return RAIL_POWER_LIMIT;
    }

    public static void setRailPowerLimit(int powerLimit) {
        RAIL_POWER_LIMIT = powerLimit;
    }

    public static void updateState(PoweredRailBlock self, BlockState state, Level level, BlockPos pos) {
        boolean shouldBePowered = level.hasNeighborSignal(pos) ||
            self.findPoweredRailSignal(level, pos, state, true, 0) ||
            self.findPoweredRailSignal(level, pos, state, false, 0);
        if (shouldBePowered != state.getValue(POWERED)) {
            RailShape railShape = state.getValue(SHAPE);
            if (railShape.isSlope()) {
                level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 3);
                level.updateNeighborsAtExceptFromFacing(pos.below(), self, Direction.UP, null);
                level.updateNeighborsAtExceptFromFacing(pos.above(), self, Direction.DOWN, null); // isSlope
            } else if (shouldBePowered) {
                powerLane(self, level, pos, state, railShape);
            } else {
                dePowerLane(self, level, pos, state, railShape);
            }
        }
    }

    private static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level world, BlockPos pos,
                                                       boolean bl, int distance, RailShape shape,
                                                       HashMap<BlockPos, Boolean> checkedPos) {
        BlockState blockState = world.getBlockState(pos);
        boolean speedCheck = checkedPos.containsKey(pos) && checkedPos.get(pos);
        if (speedCheck) {
            return world.hasNeighborSignal(pos) ||
                findPoweredRailSignalFaster(self, world, pos, blockState, bl, distance + 1, checkedPos);
        } else {
            if (blockState.is(self)) {
                RailShape railShape = blockState.getValue(SHAPE);
                if (shape == RailShape.EAST_WEST && (
                    railShape == RailShape.NORTH_SOUTH ||
                        railShape == RailShape.ASCENDING_NORTH ||
                        railShape == RailShape.ASCENDING_SOUTH
                ) || shape == RailShape.NORTH_SOUTH && (
                    railShape == RailShape.EAST_WEST ||
                        railShape == RailShape.ASCENDING_EAST ||
                        railShape == RailShape.ASCENDING_WEST
                )) {
                    return false;
                } else if (blockState.getValue(POWERED)) {
                    return world.hasNeighborSignal(pos) ||
                        findPoweredRailSignalFaster(self, world, pos, blockState, bl, distance + 1, checkedPos);
                } else {
                    return false;
                }
            }
            return false;
        }
    }

    private static boolean findPoweredRailSignalFaster(PoweredRailBlock self, Level level,
                                                       BlockPos pos, BlockState state, boolean bl, int distance,
                                                       HashMap<BlockPos, Boolean> checkedPos) {
        if (distance >= RAIL_POWER_LIMIT - 1) return false;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        boolean bl2 = true;
        RailShape railShape = state.getValue(SHAPE);
        switch (railShape.ordinal()) {
            case 0 -> {
                if (bl) ++k;
                else --k;
            }
            case 1 -> {
                if (bl) --i;
                else ++i;
            }
            case 2 -> {
                if (bl) {
                    --i;
                } else {
                    ++i;
                    ++j;
                    bl2 = false;
                }
                railShape = RailShape.EAST_WEST;
            }
            case 3 -> {
                if (bl) {
                    --i;
                    ++j;
                    bl2 = false;
                } else {
                    ++i;
                }
                railShape = RailShape.EAST_WEST;
            }
            case 4 -> {
                if (bl) {
                    ++k;
                } else {
                    --k;
                    ++j;
                    bl2 = false;
                }
                railShape = RailShape.NORTH_SOUTH;
            }
            case 5 -> {
                if (bl) {
                    ++k;
                    ++j;
                    bl2 = false;
                } else {
                    --k;
                }
                railShape = RailShape.NORTH_SOUTH;
            }
        }
        return findPoweredRailSignalFaster(
            self, level, new BlockPos(i, j, k),
            bl, distance, railShape, checkedPos
        ) ||
            (bl2 && findPoweredRailSignalFaster(
                self, level, new BlockPos(i, j - 1, k),
                bl, distance, railShape, checkedPos
            ));
    }

    private static void powerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                  BlockState mainState, RailShape railShape) {
        world.setBlock(pos, mainState.setValue(POWERED, true), UPDATE_FORCE_PLACE);
        HashMap<BlockPos, Boolean> checkedPos = new HashMap<>();
        checkedPos.put(pos, true);
        int[] count = new int[2];
        if (railShape == RailShape.NORTH_SOUTH) { // Order: +z, -z
            for (int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                setRailPositionsPower(self, world, pos, checkedPos, count, i, NORTH_SOUTH_DIR[i]);
            }
            updateRails(self, false, world, pos, mainState, count);
        } else if (railShape == RailShape.EAST_WEST) { // Order: -x, +x
            for (int i = 0; i < EAST_WEST_DIR.length; ++i) {
                setRailPositionsPower(self, world, pos, checkedPos, count, i, EAST_WEST_DIR[i]);
            }
            updateRails(self, true, world, pos, mainState, count);
        }
    }

    private static void dePowerLane(PoweredRailBlock self, Level world, BlockPos pos,
                                    BlockState mainState, RailShape railShape) {
        world.setBlock(pos, mainState.setValue(POWERED, false), UPDATE_FORCE_PLACE);
        int[] count = new int[2];
        if (railShape == RailShape.NORTH_SOUTH) { // Order: +z, -z
            for (int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                setRailPositionsDePower(self, world, pos, count, i, NORTH_SOUTH_DIR[i]);
            }
            updateRails(self, false, world, pos, mainState, count);
        } else if (railShape == RailShape.EAST_WEST) { // Order: -x, +x
            for (int i = 0; i < EAST_WEST_DIR.length; ++i) {
                setRailPositionsDePower(self, world, pos, count, i, EAST_WEST_DIR[i]);
            }
            updateRails(self, true, world, pos, mainState, count);
        }
    }

    private static void setRailPositionsPower(PoweredRailBlock self, Level world, BlockPos pos,
                                              HashMap<BlockPos, Boolean> checkedPos, int[] count, int i, Direction dir) {
        for (int z = 1; z < RAIL_POWER_LIMIT; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            if (checkedPos.containsKey(newPos)) {
                if (!checkedPos.get(newPos)) break;
                count[i]++;
            } else if (!state.is(self) || state.getValue(POWERED) || !(
                world.hasNeighborSignal(newPos) ||
                    findPoweredRailSignalFaster(self, world, newPos, state, true, 0, checkedPos) ||
                    findPoweredRailSignalFaster(self, world, newPos, state, false, 0, checkedPos)
            )) {
                checkedPos.put(newPos, false);
                break;
            } else {
                checkedPos.put(newPos, true);
                world.setBlock(newPos, state.setValue(POWERED, true), UPDATE_FORCE_PLACE);
                count[i]++;
            }
        }
    }

    private static void setRailPositionsDePower(PoweredRailBlock self, Level world, BlockPos pos,
                                                int[] count, int i, Direction dir) {
        for (int z = 1; z < RAIL_POWER_LIMIT; z++) {
            BlockPos newPos = pos.relative(dir, z);
            BlockState state = world.getBlockState(newPos);
            if (!state.is(self) || !state.getValue(POWERED) || world.hasNeighborSignal(newPos) ||
                self.findPoweredRailSignal(world, newPos, state, true, 0) ||
                self.findPoweredRailSignal(world, newPos, state, false, 0)) break;
            world.setBlock(newPos, state.setValue(POWERED, false), UPDATE_FORCE_PLACE);
            count[i]++;
        }
    }

    private static void shapeUpdateEnd(PoweredRailBlock self, Level world, BlockPos pos, BlockState mainState,
                                       int endPos, Direction direction, int currentPos, BlockPos blockPos) {
        if (currentPos == endPos) {
            BlockPos newPos = pos.relative(direction, currentPos + 1);
            giveShapeUpdate(world, mainState, newPos, pos, direction);
            BlockState state = world.getBlockState(blockPos);
            if (state.is(self) && state.getValue(SHAPE).isSlope()) giveShapeUpdate(world, mainState, newPos.above(), pos, direction);
        }
    }

    private static void neighborUpdateEnd(PoweredRailBlock self, Level world, BlockPos pos, int endPos,
                                          Direction direction, Block block, int currentPos, BlockPos blockPos) {
        if (currentPos == endPos) {
            BlockPos newPos = pos.relative(direction, currentPos + 1);
            world.neighborChanged(newPos, block, null);
            BlockState state = world.getBlockState(blockPos);
            if (state.is(self) && state.getValue(SHAPE).isSlope()) world.neighborChanged(newPos.above(), block, null);
        }
    }

    private static void updateRailsSectionEastWestShape(PoweredRailBlock self, Level world, BlockPos pos,
                                                        int c, BlockState mainState, Direction dir,
                                                        int[] count, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        if (c == 0 && count[1] == 0) giveShapeUpdate(world, mainState, pos1.relative(dir.getOpposite()), pos, dir.getOpposite());
        shapeUpdateEnd(self, world, pos, mainState, countAmt, dir, c, pos1);
        giveShapeUpdate(world, mainState, pos1.below(), pos, Direction.DOWN);
        giveShapeUpdate(world, mainState, pos1.above(), pos, Direction.UP);
        giveShapeUpdate(world, mainState, pos1.north(), pos, Direction.NORTH);
        giveShapeUpdate(world, mainState, pos1.south(), pos, Direction.SOUTH);
    }

    private static void updateRailsSectionNorthSouthShape(PoweredRailBlock self, Level world, BlockPos pos,
                                                          int c, BlockState mainState, Direction dir,
                                                          int[] count, int countAmt) {
        BlockPos pos1 = pos.relative(dir, c);
        giveShapeUpdate(world, mainState, pos1.west(), pos, Direction.WEST);
        giveShapeUpdate(world, mainState, pos1.east(), pos, Direction.EAST);
        giveShapeUpdate(world, mainState, pos1.below(), pos, Direction.DOWN);
        giveShapeUpdate(world, mainState, pos1.above(), pos, Direction.UP);
        shapeUpdateEnd(self, world, pos, mainState, countAmt, dir, c, pos1);
        if (c == 0 && count[1] == 0) giveShapeUpdate(world, mainState, pos1.relative(dir.getOpposite()), pos, dir.getOpposite());
    }

    private static void updateRails(PoweredRailBlock self, boolean eastWest, Level world,
                                    BlockPos pos, BlockState mainState, int[] count) {
        if (eastWest) {
            for (int i = 0; i < EAST_WEST_DIR.length; ++i) {
                int countAmt = count[i];
                if (i == 1 && countAmt == 0) continue;
                Direction dir = EAST_WEST_DIR[i];
                Block block = mainState.getBlock();
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir, c);
                    if (c == 0 && count[1] == 0) world.neighborChanged(p.relative(dir.getOpposite()), block, null);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    world.neighborChanged(p.below(), block, null);
                    world.neighborChanged(p.above(), block, null);
                    world.neighborChanged(p.north(), block, null);
                    world.neighborChanged(p.south(), block, null);
                    BlockPos pos2 = pos.relative(dir, c).below();
                    world.neighborChanged(pos2.below(), block, null);
                    world.neighborChanged(pos2.north(), block, null);
                    world.neighborChanged(pos2.south(), block, null);
                    if (c == countAmt) world.neighborChanged(pos.relative(dir, c + 1).below(), block, null);
                    if (c == 0 && count[1] == 0) world.neighborChanged(p.relative(dir.getOpposite()).below(), block, null);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionEastWestShape(self, world, pos, c, mainState, dir, count, countAmt);
            }
        } else {
            for (int i = 0; i < NORTH_SOUTH_DIR.length; ++i) {
                int countAmt = count[i];
                if (i == 1 && countAmt == 0) continue;
                Direction dir = NORTH_SOUTH_DIR[i];
                Block block = mainState.getBlock();
                for (int c = countAmt; c >= i; c--) {
                    BlockPos p = pos.relative(dir, c);
                    world.neighborChanged(p.west(), block, null);
                    world.neighborChanged(p.east(), block, null);
                    world.neighborChanged(p.below(), block, null);
                    world.neighborChanged(p.above(), block, null);
                    neighborUpdateEnd(self, world, pos, countAmt, dir, block, c, p);
                    if (c == 0 && count[1] == 0) world.neighborChanged(p.relative(dir.getOpposite()), block, null);
                    BlockPos pos2 = pos.relative(dir, c).below();
                    world.neighborChanged(pos2.west(), block, null);
                    world.neighborChanged(pos2.east(), block, null);
                    world.neighborChanged(pos2.below(), block, null);
                    if (c == countAmt) world.neighborChanged(pos.relative(dir, c + 1).below(), block, null);
                    if (c == 0 && count[1] == 0) world.neighborChanged(p.relative(dir.getOpposite()).below(), block, null);
                }
                for (int c = countAmt; c >= i; c--)
                    updateRailsSectionNorthSouthShape(self, world, pos, c, mainState, dir, count, countAmt);
            }
        }
    }
}
