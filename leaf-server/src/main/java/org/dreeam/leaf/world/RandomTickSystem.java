package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.material.FluidState;

import java.util.OptionalLong;

public final class RandomTickSystem {
    private static final long SCALE = 0x100000L;
    private static final long SCALE_HALF = 0x80000L;
    private static final int BITS = 20;
    private static final long CHUNK_BLOCKS = 4096L;

    private final LongArrayList queue = new LongArrayList();
    private final LongArrayList samples = new LongArrayList();
    private final LongArrayList weights = new LongArrayList();
    private long weightsSum = 0;

    private int bits = 60;
    private long cacheRandom = 0L;

    public void tick(ServerLevel world) {
        if (weights.isEmpty() || samples.isEmpty()) {
            return;
        }

        var random = world.simpleRandom;
        int spin = random.next(BITS);

        int chosen = (weightsSum >= SCALE_HALF ? random.nextInt((int) (weightsSum / SCALE_HALF)) : 0)
            + (((int) (weightsSum % SCALE_HALF) >= random.next(BITS - 1)) ? 1 : 0);
        if (chosen == 0) {
            return;
        }
        long spoke = weightsSum / chosen;
        long[] weightsRaw = weights.elements();
        long[] samplesRaw = samples.elements();
        long accumulated = weightsRaw[0];
        long current = spin;
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
            queue.push(samplesRaw[i]);
        }
        weightsSum = 0;
        weights.clear();
        samples.clear();

        var simpleRandom = world.simpleRandom;
        int j = queue.size();
        long[] queueRaw = queue.elements();
        int k = j - 3;
        // optimize getChunk for large ticking block or tick speed
        for (i = 0; i < k; i += 4) {
            long packed1 = queueRaw[i];
            long packed2 = queueRaw[i + 1];
            long packed3 = queueRaw[i + 2];
            long packed4 = queueRaw[i + 3];
            int x;
            int z;
            LevelChunk chunk1;
            LevelChunk chunk2;
            LevelChunk chunk3;
            LevelChunk chunk4;

            x = (int) packed1;
            z = (int) (packed1 >> 32);
            chunk1 = world.chunkSource.getChunkAtIfLoadedImmediately(x, z);

            if (packed1 != packed2) {
                x = (int) packed2;
                z = (int) (packed2 >> 32);
                chunk2 = world.chunkSource.getChunkAtIfLoadedImmediately(x, z);
            } else {
                chunk2 = chunk1;
            }
            if (packed2 != packed3) {
                x = (int) packed3;
                z = (int) (packed3 >> 32);
                chunk3 = world.chunkSource.getChunkAtIfLoadedImmediately(x, z);
            } else {
                chunk3 = chunk2;
            }
            if (packed3 != packed4) {
                x = (int) packed4;
                z = (int) (packed4 >> 32);
                chunk4 = world.chunkSource.getChunkAtIfLoadedImmediately(x, z);
            } else {
                chunk4 = chunk3;
            }
            if (chunk1 != null) {
                tickBlock(world, chunk1, simpleRandom);
            }
            if (chunk2 != null) {
                tickBlock(world, chunk2, simpleRandom);
            }
            if (chunk3 != null) {
                tickBlock(world, chunk3, simpleRandom);
            }
            if (chunk4 != null) {
                tickBlock(world, chunk4, simpleRandom);
            }
        }
        for (; i < j; i++) {
            long packed = queueRaw[i];
            int x = (int) packed;
            int z = (int) (packed >> 32);
            LevelChunk chunk = world.chunkSource.getChunkAtIfLoadedImmediately(x, z);
            if (chunk != null) {
                tickBlock(world, chunk, simpleRandom);
            }
        }

        queue.clear();
    }

    private static void tickBlock(ServerLevel world, LevelChunk chunk, RandomSource random) {
        OptionalLong optionalPos = chunk.leaf$getTickingPos(random.nextInt(chunk.leaf$lastTickingBlocksCount()));
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

    public void randomTickChunk(
        BitRandomSource random,
        LevelChunk chunk,
        long tickSpeed
    ) {
        if (this.bits == 60) {
            this.bits = 0;
            this.cacheRandom = random.nextLong();
        } else {
            this.bits += 2;
        }
        if ((this.cacheRandom & (0b11L << bits)) == 0L && chunk.leaf$tickingBlocksCount() != 0L) {
            long chance = (4 * tickSpeed * chunk.leaf$lastTickingBlocksCount() * SCALE) / CHUNK_BLOCKS;
            samples.add(chunk.getPos().longKey);
            weights.add(chance);
            weightsSum += chance;
        }
    }
}
