package org.leavesmc.leaves.redstone;

import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.minecraft.world.level.block.RedStoneWireBlock.POWER;

public class RedstoneWireTurbo {

    private static final ThreadLocal<Deque<BlockPos>> updateQueue = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Set<BlockPos>> queued = ThreadLocal.withInitial(Sets::newHashSet);
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);

    // Directional propagation tracking
    private static final ThreadLocal<Set<BlockPos>> currentlyUpdating = ThreadLocal.withInitial(Sets::newHashSet);

    // Power cache
    private static final ThreadLocal<Map<BlockPos, Integer>> powerCache = ThreadLocal.withInitial(() -> new HashMap<>(256));

    // Spatial partitioning scheduler
    private static final ThreadLocal<RedstoneUpdateScheduler> scheduler = ThreadLocal.withInitial(RedstoneUpdateScheduler::new);

    // Mutable BlockPos pool
    private static final ThreadLocal<ArrayDeque<BlockPos.MutableBlockPos>> mutablePosPool = ThreadLocal.withInitial(ArrayDeque::new);

    public static void updatePowerStrength(RedStoneWireBlock block, Level level, BlockPos pos, BlockState state) {
        if (isProcessing.get()) {
            if (!queued.get().contains(pos)) {
                updateQueue.get().add(pos.immutable());
                queued.get().add(pos);
            }
            return;
        }

        isProcessing.set(true);
        try {
            // Process the initial update
            processUpdate(block, level, pos, state);
            
            // Process all queued updates using the optimized scheduler
            if (!updateQueue.get().isEmpty()) {
                scheduler.get().processBatch(level, block, updateQueue.get());
            }
        } finally {
            isProcessing.set(false);
            updateQueue.get().clear();
            queued.get().clear();
            powerCache.get().clear();
            currentlyUpdating.get().clear();
        }
    }

    private static void processUpdate(RedStoneWireBlock block, Level level, BlockPos pos, BlockState state) {
        if (currentlyUpdating.get().contains(pos)) {
            return;
        }
        
        currentlyUpdating.get().add(pos);
        try {
            int oldPower = state.getValue(POWER);
            int newPower = calculateOptimizedTargetStrength(block, level, pos, oldPower);

            if (oldPower != newPower) {
                if (level.getBlockState(pos) == state) {
                    level.setBlock(pos, state.setValue(POWER, newPower), 2);
                }
                scheduleOptimizedNeighborUpdates(level, pos, block, oldPower, newPower);
            }
        } finally {
            currentlyUpdating.get().remove(pos);
        }
    }

    private static int calculateOptimizedTargetStrength(RedStoneWireBlock block, Level level, BlockPos pos, int currentPower) {
        // Check cache first
        Integer cached = powerCache.get().get(pos);
        if (cached != null) {
            return cached;
        }

        int power = 0;
        BlockPos.MutableBlockPos mutablePos = acquireMutablePos();
        
        try {
            // Check horizontal directions for wire and block power
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                mutablePos.setWithOffset(pos, direction);
                int directionPower = getWirePowerFromDirection(level, mutablePos, direction);
                power = Math.max(power, directionPower);
                if (power >= 15) break;
            }

            // Check vertical directions for direct power (torches, repeaters, etc.)
            if (power < 15) {
                for (Direction verticalDir : new Direction[]{Direction.UP, Direction.DOWN}) {
                    mutablePos.setWithOffset(pos, verticalDir);
                    int verticalPower = getDirectPowerToWire(level, mutablePos, verticalDir);
                    power = Math.max(power, verticalPower);
                    if (power >= 15) break;
                }
            }
        } finally {
            releaseMutablePos(mutablePos);
        }

        int result = Math.max(power - 1, 0);
        powerCache.get().put(pos.immutable(), result);
        return result;
    }

    private static int getWirePowerFromDirection(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RedStoneWireBlock) {
            return state.getValue(POWER);
        }
        return state.getSignal(level, pos, direction);
    }

    private static int getDirectPowerToWire(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        // For blocks above/below, check if they're providing direct power to the wire
        if (state.isSignalSource()) {
            return state.getDirectSignal(level, pos, direction.getOpposite());
        }
        return 0;
    }

    private static void scheduleOptimizedNeighborUpdates(Level level, BlockPos pos, RedStoneWireBlock block, 
                                                       int oldPower, int newPower) {
        Set<BlockPos> updates = Sets.newHashSet();

        // Always update immediate neighbors
        for (Direction dir : Direction.values()) {
            updates.add(pos.relative(dir));
        }

        // For significant power changes, update additional blocks that might be affected
        if (Math.abs(newPower - oldPower) > 1) {
            addSignificantChangeUpdates(level, pos, updates, newPower);
        }

        // Schedule all updates through the chunk-based scheduler
        for (BlockPos updatePos : updates) {
            if (level.isLoaded(updatePos)) {
                scheduler.get().scheduleUpdate(level, updatePos, block);
            }
        }
    }

    private static void addSignificantChangeUpdates(Level level, BlockPos pos, Set<BlockPos> updates, int newPower) {
        // Only add a few additional positions for significant power changes
        // This is more conservative than the radius-based approach
        if (newPower > 10) {
            // For very strong signals, update diagonal neighbors
            for (Direction dir1 : Direction.Plane.HORIZONTAL) {
                for (Direction dir2 : Direction.Plane.HORIZONTAL) {
                    if (dir1 != dir2 && dir1 != dir2.getOpposite()) {
                        updates.add(pos.relative(dir1).relative(dir2));
                    }
                }
            }
        }
    }

    static class RedstoneUpdateScheduler {
        private final Map<ChunkPos, Set<BlockPos>> chunkUpdates = new HashMap<>();

        public void scheduleUpdate(Level level, BlockPos pos, RedStoneWireBlock block) {
            ChunkPos chunkPos = new ChunkPos(pos);
            chunkUpdates.computeIfAbsent(chunkPos, k -> Sets.newHashSet()).add(pos.immutable());
        }

        public void processBatch(Level level, RedStoneWireBlock block, Deque<BlockPos> additionalUpdates) {
            // Add any additional updates from the queue
            for (BlockPos pos : additionalUpdates) {
                if (level.isLoaded(pos)) {
                    scheduleUpdate(level, pos, block);
                }
            }

            // Process all updates chunk by chunk
            for (Map.Entry<ChunkPos, Set<BlockPos>> entry : chunkUpdates.entrySet()) {
                ChunkPos chunkPos = entry.getKey();
                if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                    processChunkUpdates(level, entry.getValue(), block);
                }
            }
            chunkUpdates.clear();
        }

        private void processChunkUpdates(Level level, Set<BlockPos> positions, RedStoneWireBlock block) {
            for (BlockPos pos : positions) {
                // Skip if already updating this position
                if (currentlyUpdating.get().contains(pos)) continue;
                
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof RedStoneWireBlock) {
                    processUpdate(block, level, pos, state);
                }
            }
        }
    }

    private static BlockPos.MutableBlockPos acquireMutablePos() {
        ArrayDeque<BlockPos.MutableBlockPos> pool = mutablePosPool.get();
        return pool.isEmpty() ? new BlockPos.MutableBlockPos() : pool.pollLast();
    }

    private static void releaseMutablePos(BlockPos.MutableBlockPos pos) {
        ArrayDeque<BlockPos.MutableBlockPos> pool = mutablePosPool.get();
        if (pool.size() < 64) {
            pool.addLast(pos);
        }
    }

    // Cleanup method for thread shutdown
    public static void cleanup() {
        updateQueue.remove();
        queued.remove();
        isProcessing.remove();
        currentlyUpdating.remove();
        powerCache.remove();
        scheduler.remove();
        mutablePosPool.remove();
    }
}