package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.material.FluidState;

import java.util.Arrays;

public final class RandomTickSystem {
    private static final long SCALE = 0x100000L;
    private static final long CHUNK_BLOCKS = 4096L;

    /// reduce unnecessary sampling and block counting
    private static final long TICK_MASK = 0b11L;
    private static final long TICK_MUL = 4L;
    private static final int BITS_STEP = 2;
    private static final int BITS_MAX = 60;

    private final LongArrayList queue = new LongArrayList();
    private final LongArrayList samples = new LongArrayList();
    private final LongArrayList weights = new LongArrayList();
    private long weightsSum = 0L;

    private int bits = 60;
    private long cacheRandom = 0L;

    public void tick(ServerLevel world) {
        if (weights.isEmpty() || samples.isEmpty()) {
            return;
        }

        final var random = world.simpleRandom;
        final long chosen;
        if (((weightsSum % SCALE) >= boundedNextLong(random, SCALE))) {
            chosen = weightsSum / SCALE + 1L;
        } else {
            chosen = weightsSum / SCALE;
        }
        if (chosen == 0L) {
            return;
        }

        final long spoke = weightsSum / chosen;
        if (spoke == 0L) {
            return;
        }

        final long[] weightsRaw = weights.elements();
        final long[] samplesRaw = samples.elements();

        long accumulated = weightsRaw[0];
        long current = boundedNextLong(random, spoke);
        int i = 0;
        while (current < weightsSum) {
            while (accumulated < current) {
                i += 1;
                accumulated += weightsRaw[i];
            }
            queue.add(samplesRaw[i]);
            current += spoke;
        }
        while (queue.size() < chosen) {
            queue.add(samplesRaw[i]);
        }

        long[] queueRaw = queue.elements();
        int j = 0;
        int k;
        for (k = queue.size() - 3; j < k; j += 4) {
            final long packed1 = queueRaw[j];
            final long packed2 = queueRaw[j + 1];
            final long packed3 = queueRaw[j + 2];
            final long packed4 = queueRaw[j + 3];
            final LevelChunk chunk1 = getChunk(world, packed1);
            final LevelChunk chunk2 = packed1 != packed2 ? getChunk(world, packed2) : chunk1;
            final LevelChunk chunk3 = packed2 != packed3 ? getChunk(world, packed3) : chunk2;
            final LevelChunk chunk4 = packed3 != packed4 ? getChunk(world, packed4) : chunk3;
            if (chunk1 != null) tickBlock(world, chunk1, random);
            if (chunk2 != null) tickBlock(world, chunk2, random);
            if (chunk3 != null) tickBlock(world, chunk3, random);
            if (chunk4 != null) tickBlock(world, chunk4, random);
        }
        for (k = queue.size(); j < k; j++) {
            LevelChunk chunk = getChunk(world, queueRaw[j]);
            if (chunk != null) tickBlock(world, chunk, random);
        }

        weightsSum = 0L;
        queue.clear();
        weights.clear();
        samples.clear();
    }

    private static LevelChunk getChunk(ServerLevel world, long packed) {
        return world.chunkSource.getChunkAtIfLoadedImmediately((int) packed, (int) (packed >> 32));
    }

    private static void tickBlock(ServerLevel world, LevelChunk chunk, BitRandomSource random) {
        if (chunk.leaf$firstTickingSectionIndex == -1) {
            return;
        }
        int idx = random.nextInt(chunk.leaf$tickingBlocksCount);
        LevelChunkSection[] sections = chunk.getSections();
        int cx = chunk.locX;
        int cz = chunk.locZ;
        BlockPos pos = null;
        for (int i = chunk.leaf$firstTickingSectionIndex; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            var l = section.tickingBlocks;
            int size = l.size();
            if (idx < size) {
                short loc = l.get(random);
                int x = (loc & 15) | (cx << 4);
                int y = (loc >>> 8) | ((chunk.getMinSectionY() + i) << 4);
                int z = ((loc >>> 4) & 15) | (cz << 4);
                pos = new BlockPos(x, y, z);
                break;
            }
            idx -= size;
        }
        if (pos == null) {
            chunk.leaf$tickingBlocksDirty = true;
            chunk.leaf$firstTickingSectionIndex = -1;
            return;
        }

        BlockState state = chunk.getBlockStateFinal(pos.getX(), pos.getY(), pos.getZ());
        state.randomTick(world, pos, random);

        final boolean doubleTickFluids = !ca.spottedleaf.moonrise.common.PlatformHooks.get().configFixMC224294();
        if (doubleTickFluids) {
            final FluidState fluidState = state.getFluidState();
            if (fluidState.isRandomlyTicking()) {
                fluidState.randomTick(world, pos, random);
            }
        }
    }

    public void tickChunk(
        BitRandomSource random,
        LevelChunk chunk,
        long tickSpeed
    ) {
        if (this.bits == BITS_MAX) {
            this.bits = 0;
            this.cacheRandom = random.nextLong();
        } else {
            this.bits += BITS_STEP;
        }
        if ((this.cacheRandom & (TICK_MASK << bits)) != 0L) {
            return;
        }
        if (chunk.leaf$tickingBlocksDirty) {
            chunk.leaf$tickingBlocksDirty = false;
            int sum = 0;
            chunk.leaf$firstTickingSectionIndex = -1;
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                int size = section.tickingBlocks.size();
                if (size != 0 && chunk.leaf$firstTickingSectionIndex == -1) {
                    chunk.leaf$firstTickingSectionIndex = i;
                }
                sum += size;
            }
            chunk.leaf$tickingBlocksCount = sum;
        }
        int count = chunk.leaf$tickingBlocksCount;
        if (count != 0L) {
            long weight = (TICK_MUL * tickSpeed * count * SCALE) / CHUNK_BLOCKS;
            samples.add(chunk.getPos().longKey);
            weights.add(weight);
            weightsSum += weight;
        }
    }

    /**
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code long} values
     * @param bound the upper bound (exclusive); must be greater than zero
     *
     * @return a pseudorandomly chosen {@code long} value
     *
     * @see java.util.random.RandomGenerator#nextLong(long) nextLong(bound)
     */
    public static long boundedNextLong(BitRandomSource rng, long bound) {
        final long m = bound - 1;
        long r = rng.nextLong();
        if ((bound & m) == 0L) {
            r &= m;
        } else {
            for (long u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = rng.nextLong() >>> 1)
                ;
        }
        return r;
    }

    public static final class TickingBlockSet {
        private static final short EMPTY = -1;
        private static final short[] EMPTY_ARRAY = {};
        private static final int DEFAULT_CAP = 8;

        private short[] a = EMPTY_ARRAY;
        private int size;
        private int bits;

        public void clear() {
            a = EMPTY_ARRAY;
            size = 0;
            bits = 0;
        }

        /// @param n {@code n >= 0 && n <= 4096}
        public boolean add(short n) {
            if (a == EMPTY_ARRAY) {
                a = new short[DEFAULT_CAP];
                Arrays.fill(a, EMPTY);
                bits = Integer.numberOfTrailingZeros(DEFAULT_CAP);
            }
            return addShort(n);
        }

        /// @param n {@code n >= 0 && n <= 4096}
        private boolean addShort(short n) {
            if (size >= a.length >>> 1) {
                resize(a.length << 1);
            }
            int i = HashCommon.mix(n) & (a.length - 1);
            int start = i;
            do {
                if (a[i] == n) {
                    return false;
                }
                if (a[i] == EMPTY) {
                    a[i] = n;
                    size++;
                    return true;
                }
                i = (i + 1) & (a.length - 1);
            } while (i != start);
            return false;
        }

        /// @param n {@code n >= 0 && n <= 4096}
        public boolean remove(short n) {
            if (size == 0) {
                return false;
            }
            int i = HashCommon.mix(n) & (a.length - 1);
            int start = i;
            do {
                if (a[i] == n) {
                    a[i] = EMPTY;
                    size--;
                    i = (i + 1) & (a.length - 1);
                    while (a[i] != EMPTY) {
                        short rehash = a[i];
                        a[i] = EMPTY;
                        size--;
                        addShort(rehash);
                        i = (i + 1) & (a.length - 1);
                    }
                    return true;
                }
                if (a[i] == EMPTY) {
                    return false;
                }
                i = (i + 1) & (a.length - 1);
            } while (i != start);
            return false;
        }

        public short get(BitRandomSource rand) {
            if (size == 0) return EMPTY;
            while (true) {
                int i = rand.next(bits);
                if (a[i] != EMPTY) return a[i];
            }
        }

        public int size() {
            return size;
        }

        private void resize(int cap) {
            short[] o = a;
            a = new short[cap];
            Arrays.fill(a, EMPTY);
            size = 0;
            bits = Integer.numberOfTrailingZeros(cap);

            for (short val : o) {
                if (val != EMPTY) {
                    addShort(val);
                }
            }
        }
    }
}
