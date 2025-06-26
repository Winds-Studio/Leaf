package org.dreeam.leaf.world;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.list.ShortList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.material.FluidState;

public final class RandomTickSystem {
    private static final long SCALE = 0x100000L;
    private static final long TICK_FILTER_MASK = 0b11L;
    private static final long CHUNK_BLOCKS = 4096L / 4L;
    private static final int BITS_STEP = 2;
    private static final int BITS_MAX = 60;

    private final IntArrayList queue = new IntArrayList();
    private final ShortArrayList yList = new ShortArrayList();
    private final IntArrayList xzList = new IntArrayList();
    private final LongArrayList weightList = new LongArrayList();

    public void tick(ServerLevel world) {
        queue.clear();
        yList.clear();
        xzList.clear();
        weightList.clear();

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
        final long weightsSum = collectTickingChunks(size, random, raw, randomTickSpeed);
        if (weightList.isEmpty() || yList.isEmpty() || weightsSum == 0L) {
            return;
        }
        sampling(random, weightsSum);

        final int[] q = queue.elements();
        final int qs = queue.size();
        final short[] yl = yList.elements();
        final int[] xzl = xzList.elements();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(world);
        for (int k = 0; k < qs; k++) {
            final int index = q[k];
            final LevelChunk a = raw[xzl[index]];
            if (a != null) {
                tickBlock(world, a, yl[index], random, minSection);
            }
        }
    }

    private void sampling(BitRandomSource random, long weightsSum) {
        final long chosen;
        if (((weightsSum % SCALE) >= boundedNextLong(random, SCALE))) {
            chosen = weightsSum / SCALE + 1L;
        } else {
            chosen = weightsSum / SCALE;
        }
        if (chosen == 0L) {
            return;
        }

        final long[] weightsRaw = weightList.elements();
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
            queue.add(i);
            current += spoke;
        }
    }

    private long collectTickingChunks(int size, BitRandomSource random, LevelChunk[] raw, long randomTickSpeed) {
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
            if (chunk.leaf$tickingBlocksDirty) {
                populateChunkTickingCount(chunk);
            }
            short[] count = chunk.leaf$tickingBlocksCount;
            short[] idx = chunk.leaf$tickingIdx;
            for (int j = 0; j < count.length; j++) {
                long weight = (randomTickSpeed * ((long) count[j]) * SCALE) / CHUNK_BLOCKS;
                yList.add(idx[j]);
                xzList.add(i);
                weightList.add(weight);
                weightsSum += weight;
            }
        }
        return weightsSum;
    }

    private static void populateChunkTickingCount(LevelChunk chunk) {
        chunk.leaf$tickingBlocksDirty = false;
        int sum = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            sum += (section.moonrise$getTickingBlockList().size() == 0) ? 0 : 1;
        }
        if (chunk.leaf$tickingBlocksCount.length != sum) {
            chunk.leaf$tickingBlocksCount = new short[sum];
            chunk.leaf$tickingIdx = new short[sum];
        }
        short[] count = chunk.leaf$tickingBlocksCount;
        short[] idx = chunk.leaf$tickingIdx;
        int k = 0;
        for (int j = 0, sectionsLength = chunk.getSections().length; j < sectionsLength; j++) {
            LevelChunkSection section = chunk.getSections()[j];
            int n = (short) section.moonrise$getTickingBlockList().size();
            if (n != 0) {
                count[k] = (short) n;
                idx[k] = (short) j;
                k++;
            }
        }
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

    private static void tickBlock(ServerLevel world, LevelChunk chunk, int sectionIdx, BitRandomSource random, int minSection) {
        LevelChunkSection section = chunk.getSection(sectionIdx);
        ShortList list = section.moonrise$getTickingBlockList();
        int sz = list.size();
        if (sz == 0) return;
        short location = list.getRaw(boundedNextInt(random, sz));
        BlockState state = section.states.get(location);
        final BlockPos pos = new BlockPos((location & 15) | chunk.locX << 4, (location >>> (4 + 4)) | (sectionIdx + minSection) << 4, ((location >>> 4) & 15) | chunk.locZ << 4);
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

    private static int boundedNextInt(BitRandomSource rng, int bound) {
        final int m = bound - 1;
        int r = rng.nextInt();
        if ((bound & m) == 0) {
            r &= m;
        } else {
            //noinspection StatementWithEmptyBody
            for (int u = r >>> 1;
                 u + m - (r = u % bound) < 0;
                 u = rng.nextInt() >>> 1)
                ;
        }
        return r;
    }
}
