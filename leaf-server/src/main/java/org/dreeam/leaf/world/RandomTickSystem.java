package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import java.util.OptionalLong;

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
            final LevelChunk chunk = getChunk(world, queueRaw[j]);
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

    private static void tickBlock(ServerLevel world, LevelChunk chunk, RandomSource random) {
        int count = chunk.leaf$tickingBlocksCount();
        if (count == 0) {
            return;
        }
        OptionalLong optionalPos = chunk.leaf$getTickingPos(random.nextInt(count));
        if (optionalPos.isEmpty()) {
            return;
        }
        BlockPos pos = BlockPos.of(optionalPos.getAsLong());
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
        RandomSource random,
        LevelChunk chunk,
        long tickSpeed
    ) {
        if (this.bits == BITS_MAX) {
            this.bits = 0;
            this.cacheRandom = random.nextLong();
        } else {
            this.bits += BITS_STEP;
        }
        if ((this.cacheRandom & (TICK_MASK << bits)) == 0L) {
            long count = chunk.leaf$tickingBlocksCount();
            if (count != 0L) {
                long weight = (TICK_MUL * tickSpeed * count * SCALE) / CHUNK_BLOCKS;
                samples.add(chunk.getPos().longKey);
                weights.add(weight);
                weightsSum += weight;
            }
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
    public static long boundedNextLong(RandomSource rng, long bound) {
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
}
