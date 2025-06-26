package org.dreeam.leaf.world;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.moonrise.common.list.ReferenceList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.material.FluidState;

import java.util.OptionalLong;

public final class RandomTickSystem {
    private static final long SCALE = 0x100000L;
    private static final long TICK_FILTER_MASK = 0b11L;
    private static final long CHUNK_BLOCKS = 4096L / 4L;
    private static final int BITS_STEP = 2;
    private static final int BITS_MAX = 60;

    private final LongArrayList queue = new LongArrayList();
    private final LongArrayList samples = new LongArrayList();
    private final LongArrayList weights = new LongArrayList();

    public void tick(ServerLevel world) {
        final BitRandomSource random = world.simpleRandom;

        final ReferenceList<LevelChunk> entityTickingChunks = world.moonrise$getEntityTickingChunks();
        final int randomTickSpeed = world.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        final LevelChunk[] raw = entityTickingChunks.getRawDataUnchecked();
        final int size = entityTickingChunks.size();
        final boolean disableIceAndSnow = world.paperConfig().environment.disableIceAndSnow;
        if (randomTickSpeed <= 0) {
            return;
        }
        if (!disableIceAndSnow) {
            iceSnow(world, size, randomTickSpeed, random, raw);
        }
        final long weightsSum = fillWeight(size, random, raw, randomTickSpeed);
        if (weights.isEmpty() || samples.isEmpty() || weightsSum == 0L) {
            return;
        }
        sus(random, weightsSum);
        weights.clear();
        samples.clear();

        final ConcurrentLong2ReferenceChainedHashTable<LevelChunk> fullChunks = world.chunkSource.fullChunks;
        final long[] q = queue.elements();
        final int l = queue.size();
        LevelChunk a = null;
        long b = 0L;
        for (int k = 0; k < l; k++) {
            final long pos = q[k];
            if (a == null || b != pos) {
                a = fullChunks.get(pos);
                b = pos;
            }
            if (a != null) {
                tickBlock(world, a, random);
            }
        }
        queue.clear();
    }

    private void sus(BitRandomSource random, long weightsSum) {
        final long chosen;
        if (((weightsSum % SCALE) >= boundedNextLong(random, SCALE))) {
            chosen = weightsSum / SCALE + 1L;
        } else {
            chosen = weightsSum / SCALE;
        }
        if (chosen == 0L) {
            return;
        }

        final long[] weightsRaw = weights.elements();
        final long[] samplesRaw = samples.elements();

        long accumulated = weightsRaw[0];
        final long spoke = weightsSum / chosen;
        if (spoke == 0L) return;
        long current = boundedNextLong(random, spoke);
        int i = 0;
        while (current < weightsSum) {
            while (accumulated < current) {
                i++;
                accumulated += weightsRaw[i];
            }
            queue.add(samplesRaw[i]);
            current += spoke;
        }
    }

    private long fillWeight(int size, BitRandomSource random, LevelChunk[] raw, long randomTickSpeed) {
        int bits = 0;
        long cacheRandom = random.nextLong();
        long weightsSum = 0L;

        for (int i = 0; i < size; i++) {
            if (bits != BITS_MAX) {
                bits += BITS_STEP;
            } else {
                bits = 0;
                cacheRandom = random.nextLong();
            }
            if ((cacheRandom & (TICK_FILTER_MASK << bits)) != 0L) {
                continue;
            }
            final LevelChunk chunk = raw[i];
            final long count = chunk.leaf$tickingBlocksCount();
            if (count != 0L) {
                long weight = (randomTickSpeed * count * SCALE) / CHUNK_BLOCKS;
                samples.add(chunk.locX & 4294967295L | (chunk.locZ & 4294967295L) << 32);
                weights.add(weight);
                weightsSum += weight;
            }
        }
        return weightsSum;
    }

    private static void iceSnow(ServerLevel world, int size, int randomTickSpeed, BitRandomSource random, LevelChunk[] raw) {
        int currentIceAndSnowTick = random.nextInt(48 * 16);
        for (int i = 0; i < size; i++) {
            currentIceAndSnowTick -= randomTickSpeed;
            if (currentIceAndSnowTick <= 0) {
                currentIceAndSnowTick = random.nextInt(48 * 16);
                LevelChunk chunk = raw[i];
                ChunkPos pos = chunk.getPos();
                int minBlockX = pos.getMinBlockX();
                int minBlockZ = pos.getMinBlockZ();
                world.tickPrecipitation(world.getBlockRandomPos(minBlockX, 0, minBlockZ, 15));
            }
        }
    }

    private static void tickBlock(ServerLevel world, LevelChunk chunk, BitRandomSource random) {
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

    private static long boundedNextLong(BitRandomSource rng, long bound) {
        final long m = bound - 1L;
        long r = rng.nextLong();
        if ((bound & m) == 0L) {
            r &= m;
        } else {
            //noinspection StatementWithEmptyBody
            for (long u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = rng.nextLong() >>> 1)
                ;
        }
        return r;
    }
}
