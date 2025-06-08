package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;

public final class RandomTickSystem {
    private final LongArrayList tickPos = new LongArrayList();
    private final WyRand rand = new WyRand(RandomSupport.generateUniqueSeed());
    private static final long SCALE = 0x100000L;
    private static final long MASK = 0xfffffL;
    private long cache = rand.next();
    private int cacheIdx = 0;

    public void tick(ServerLevel world) {
        var simpleRandom = world.simpleRandom;
        int j = tickPos.size();
        for (int i = 0; i < j; i++) {
            tickBlock(world, tickPos.getLong(i), simpleRandom);
        }
        tickPos.clear();
    }

    private static void tickBlock(ServerLevel world, long packed, RandomSource tickRand) {
        final boolean doubleTickFluids = !ca.spottedleaf.moonrise.common.PlatformHooks.get().configFixMC224294();
        BlockPos pos = BlockPos.of(packed);
        LevelChunk chunk = world.chunkSource.getChunkAtIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        BlockState state = chunk.getBlockStateFinal(pos.getX(), pos.getY(), pos.getZ());
        if (state == null) {
            return;
        }
        state.randomTick(world, pos, tickRand);
        if (doubleTickFluids) {
            final FluidState fluidState = state.getFluidState();
            if (fluidState.isRandomlyTicking()) {
                fluidState.randomTick(world, pos, tickRand);
            }
        }
    }

    private long recompute(LevelChunk chunk, long tickSpeed) {
        chunk.leaf$recompute();
        long tickingCount = chunk.leaf$countTickingBlocks();
        long numSections = chunk.leaf$countTickingSections();
        if (tickingCount == 0L || numSections == 0L) {
            chunk.leaf$setRandomTickChance(0L);
            return 0L;
        }
        long product = tickSpeed * tickingCount;
        long chance = ((product + 2048L) * SCALE) / 4096L;
        chunk.leaf$setRandomTickChance(chance);
        return chance;
    }
/*
    public void randomTickChunkOrigin(
        ServerLevel level,
        LevelChunk chunk,
        long tickSpeed
    ) {
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(level); // Leaf - Micro optimizations for random tick - no redundant cast
        final net.minecraft.world.level.levelgen.BitRandomSource simpleRandom = level.simpleRandom; // Leaf - Faster random generator - upcasting
        final boolean doubleTickFluids = !ca.spottedleaf.moonrise.common.PlatformHooks.get().configFixMC224294();

        final ChunkPos cpos = chunk.getPos();
        final int offsetX = cpos.x << 4;
        final int offsetZ = cpos.z << 4;

        for (int sectionIndex = 0, sectionsLen = sections.length; sectionIndex < sectionsLen; sectionIndex++) {
            // Leaf start - Micro optimizations for random tick
            final LevelChunkSection section = sections[sectionIndex];
            if (!section.isRandomlyTickingBlocks()) {
                continue;
            }
            final int offsetY = (sectionIndex + minSection) << 4;
            final net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> states = section.states;
            // Leaf end - Micro optimizations for random tick

            final ca.spottedleaf.moonrise.common.list.ShortList tickList = section.moonrise$getTickingBlockList(); // Leaf - Micro optimizations for random tick - no redundant cast

            for (int i = 0; i < tickSpeed; ++i) {
                final int index = simpleRandom.nextInt() & ((16 * 16 * 16) - 1);

                if (index >= tickList.size()) { // Leaf - Micro optimizations for random tick - inline one-time value
                    // most of the time we fall here
                    continue;
                }

                final int location = tickList.getRaw(index); // Leaf - Micro optimizations for random tick - no unnecessary operations
                final BlockState state = states.get(location);

                // do not use a mutable pos, as some random tick implementations store the input without calling immutable()!
                final BlockPos pos = new BlockPos((location & 15) | offsetX, (location >>> (4 + 4)) | offsetY, ((location >>> 4) & 15) | offsetZ); // Leaf - Micro optimizations for random tick - no redundant mask

                state.randomTick(level, pos, simpleRandom); // Leaf - Micro optimizations for random tick - no redundant cast
                if (doubleTickFluids) {
                    final FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick(level, pos, simpleRandom); // Leaf - Micro optimizations for random tick - no redundant cast
                    }
                }
            }
        }
    }
*/

    public void randomTickChunk(
        LevelChunk chunk,
        long tickSpeed
    ) {
        long a;
        if (cacheIdx == 0) {
            a = cache;
            cacheIdx = 1;
        } else if (cacheIdx == 1) {
            a = cache >>> 32;
            cacheIdx = 2;
        } else {
            a = cache = rand.next();
            cacheIdx = 0;
        }
        if ((a & 0x300000L) != 0L) {
            return;
        }
        tickSpeed = tickSpeed * 4;
        long chance = chunk.leaf$randomTickChance();
        if (chance == 0L && (chance = recompute(chunk, tickSpeed)) == 0) {
            return;
        }
        if (chance >= (a & MASK)) {
            return;
        }
        if ((chance = recompute(chunk, tickSpeed)) == 0) {
            return;
        }

        long tickingCount = chunk.leaf$countTickingBlocks();
        long shouldTick = rand.next() & MASK;
        int randPos = (int) ((rand.next() & Integer.MAX_VALUE) % tickingCount);
        OptionalLong pos = chunk.leaf$tickingPos(randPos);
        if (pos.isPresent()) {
            tickPos.add(pos.getAsLong());
        }
        while (shouldTick <= chance) {
            randPos = (int) ((rand.next() & Integer.MAX_VALUE) % tickingCount);
            pos = chunk.leaf$tickingPos(randPos);
            if (pos.isPresent()) tickPos.add(pos.getAsLong());
            chance -= SCALE;
        }
    }

    private final static class WyRand implements BitRandomSource {
        private long state;

        private static final long WY0 = 0x2d35_8dcc_aa6c_78a5L;
        private static final long WY1 = 0x8bb8_4b93_962e_acc9L;
        private static final int BITS = 64;

        public WyRand(long seed) {
            this.state = seed;
        }

        @Override
        public int next(int bits) {
            return (int)(this.next() >>> (BITS - bits));
        }

        @Override
        public @NotNull RandomSource fork() {
            return new WyRand(next());
        }

        @Override
        public @NotNull PositionalRandomFactory forkPositional() {
            throw new UnsupportedOperationException("forkPositional");
        }

        @Override
        public void setSeed(long seed) {
            this.state = seed;
        }

        public int nextInt() {
            return (int) (next() & Integer.MAX_VALUE);
        }

        @Override
        public double nextGaussian() {
            throw new UnsupportedOperationException("nextGaussian");
        }

        public long next() {
            long seed = this.state;
            seed += WY0;
            long aLow = seed & 0xFFFFFFFFL;
            long aHigh = seed >>> 32;
            long bLow = (seed ^ WY1) & 0xFFFFFFFFL;
            long bHigh = (seed ^ WY1) >>> 32;
            long loLo = aLow * bLow;
            long hiLo = aHigh * bLow;
            long loHi = aLow * bHigh;
            long hiHi = aHigh * bHigh;
            long mid1 = (loLo >>> 32) + (hiLo & 0xFFFFFFFFL) + (loHi & 0xFFFFFFFFL);
            long mid2 = (hiLo >>> 32) + (loHi >>> 32) + (mid1 >>> 32);
            this.state = seed;
            return ((loLo & 0xFFFFFFFFL) | (mid1 << 32)) ^ hiHi + (mid2 & 0xFFFFFFFFL);
        }
    }
}
