package org.dreeam.leaf.util.cache;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;

/**
 * Cache for positions computed with BlockPos.withinManhattan() away from BlockPos(0,0,0).
 * Note: POS_ZERO must not be replaced with BlockPos.ORIGIN, otherwise iterateOutwards at BlockPos.ORIGIN will not use the cache.
 * Original implementation by SuperCoder7979 and Gegy1000, modified by 2No2Name.
 */
public class IterateOutwardsCache {

    public static final BlockPos POS_ZERO = new BlockPos(0, 0, 0);

    private final ConcurrentHashMap<Long, LongArrayList> table;
    private final int capacity;

    public IterateOutwardsCache(int capacity) {
        this.capacity = capacity;
        // Pre-size the map based on expected load to reduce rehashing
        int initialCapacity = Math.max(31, capacity / 4);
        this.table = new ConcurrentHashMap<>(initialCapacity);
    }

    private void fillPositionsWithIterateOutwards(LongList entry, int xRange, int yRange, int zRange) {
        // Add all positions (as long value) within Manhattan distance from POS_ZERO.
        for (BlockPos pos : BlockPos.withinManhattan(POS_ZERO, xRange, yRange, zRange)) {
            entry.add(pos.asLong());
        }
    }

    /**
     * Retrieves a cached list of positions for the given range or computes and caches it if not present.
     */
    public LongList getOrCompute(int xRange, int yRange, int zRange) {
        long key = BlockPos.asLong(xRange, yRange, zRange);
        LongArrayList entry = this.table.get(key);
        if (entry != null) {
            return entry;
        }

        // Cache miss: compute the list.
        LongArrayList computed = new LongArrayList(128);
        fillPositionsWithIterateOutwards(computed, xRange, yRange, zRange);
        computed.trim();

        // Use putIfAbsent to avoid long compute locks; if another thread computed concurrently, use that entry.
        LongArrayList previous = this.table.putIfAbsent(key, computed);
        LongArrayList result = (previous != null) ? previous : computed;

        // If the cache size exceeds the capacity, randomly remove some entries (~1/8th chance).
        if (previous == null && this.table.size() > this.capacity) {
            Iterator<Long> iterator = this.table.keySet().iterator();
            // Avoid potential infinite loop with a small counter limit.
            for (int i = -this.capacity; iterator.hasNext() && i < 5; i++) {
                Long currentKey = iterator.next();
                // Random removal: quality of randomness isn't critical here.
                // Using ThreadLocalRandom for better performance in multi-threaded environment
                if (ThreadLocalRandom.current().nextInt(8) == 0 && currentKey != key) {
                    iterator.remove();
                }
            }
        }
        return result;
    }
}
