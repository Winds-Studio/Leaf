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
    private final LongArrayList tickPos = new LongArrayList();
    private static final long SCALE = 0x100000L;
    private static final long CHUNK_BLOCKS = 4096L;
    private static final int MASK = 0xfffff;
    private static final int MASK_ONE_FOURTH = 0x300000;

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
        long chance = (tickSpeed * tickingCount * SCALE) / CHUNK_BLOCKS;

        chunk.leaf$setRandomTickChance(chance);
        return chance;
    }

    public void randomTickChunk(
        RandomSource randomSource,
        LevelChunk chunk,
        long tickSpeed
    ) {
        int a = randomSource.nextInt();
        if ((a & MASK_ONE_FOURTH) != 0) {
            return;
        }
        tickSpeed = tickSpeed * 4;

        long chance = chunk.leaf$randomTickChance();
        if (chance == 0L && (chance = recompute(chunk, tickSpeed)) == 0L) {
            return;
        }
        if (chance <= (long) (a & MASK) || (chance = recompute(chunk, tickSpeed)) == 0L) {
            return;
        }
        int tickingCount = chunk.leaf$countTickingBlocks();
        OptionalLong pos = chunk.leaf$tickingPos(randomSource.nextInt(tickingCount));
        if (pos.isPresent()) {
            tickPos.add(pos.getAsLong());
        }

        if (chance < SCALE) {
            return;
        }
        chance -= SCALE;
        long last = randomSource.nextInt() & MASK;
        while (chance > last) {
            pos = chunk.leaf$tickingPos(randomSource.nextInt(tickingCount));
            if (pos.isPresent()) {
                tickPos.add(pos.getAsLong());
            }
            chance -= SCALE;
        }
    }
}
