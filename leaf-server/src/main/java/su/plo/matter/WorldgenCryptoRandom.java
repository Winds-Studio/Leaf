package su.plo.matter;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

@NullMarked
public class WorldgenCryptoRandom extends WorldgenRandom {

    // hash the world seed to guard against badly chosen world seeds
    private static final long[] HASHED_ZERO_SEED = Hashing.hashWorldSeed(new long[Globals.WORLD_SEED_LONGS]);
    private static final ThreadLocal<long[]> LAST_SEEN_WORLD_SEED = ThreadLocal.withInitial(() -> new long[Globals.WORLD_SEED_LONGS]);
    private static final ThreadLocal<long[]> HASHED_WORLD_SEED = ThreadLocal.withInitial(() -> HASHED_ZERO_SEED);

    private final long[] worldSeed = new long[Globals.WORLD_SEED_LONGS];
    private final long[] randomBits = new long[8];
    private int randomBitIndex;
    private static final int MAX_RANDOM_BIT_INDEX = 64 * 8;
    private static final int LOG2_MAX_RANDOM_BIT_INDEX = 9;
    private long counter;
    private final long[] message = new long[16];
    private final long[] cachedInternalState = new long[16];

    public WorldgenCryptoRandom(int x, int z, Globals.@Nullable Salt typeSalt, long salt) {
        super(org.dreeam.leaf.config.modules.opt.FastRNG.enabled ? new org.dreeam.leaf.util.math.random.FasterRandomSource(0L) : new LegacyRandomSource(0L));
        if (typeSalt != null) {
            this.setSecureSeed(x, z, typeSalt, salt);
        }
    }

    public void setSecureSeed(int x, int z, Globals.Salt typeSalt, long salt) {
        System.arraycopy(Globals.worldSeed, 0, this.worldSeed, 0, Globals.WORLD_SEED_LONGS);
        message[0] = ((long) x << 32) | ((long) z & 0xffffffffL);
        message[1] = ((long) Globals.dimension.get() << 32) | (salt & 0xffffffffL);
        message[2] = typeSalt.ordinal();
        message[3] = counter = 0;
        randomBitIndex = MAX_RANDOM_BIT_INDEX;
    }

    private long[] getHashedWorldSeed() {
        if (!Arrays.equals(worldSeed, LAST_SEEN_WORLD_SEED.get())) {
            HASHED_WORLD_SEED.set(Hashing.hashWorldSeed(worldSeed));
            System.arraycopy(worldSeed, 0, LAST_SEEN_WORLD_SEED.get(), 0, Globals.WORLD_SEED_LONGS);
        }
        return HASHED_WORLD_SEED.get();
    }

    private void moreRandomBits() {
        message[3] = counter++;
        System.arraycopy(getHashedWorldSeed(), 0, randomBits, 0, 8);
        Hashing.hash(message, randomBits, cachedInternalState, 64, true);
    }

    private long getBits(int count) {
        if (randomBitIndex >= MAX_RANDOM_BIT_INDEX) {
            moreRandomBits();
            randomBitIndex -= MAX_RANDOM_BIT_INDEX;
        }

        int alignment = randomBitIndex & 63;
        if ((randomBitIndex >>> 6) == ((randomBitIndex + count) >>> 6)) {
            long result = (randomBits[randomBitIndex >>> 6] >>> alignment) & ((1L << count) - 1);
            randomBitIndex += count;
            return result;
        } else {
            long result = (randomBits[randomBitIndex >>> 6] >>> alignment) & ((1L << (64 - alignment)) - 1);
            randomBitIndex += count;
            if (randomBitIndex >= MAX_RANDOM_BIT_INDEX) {
                moreRandomBits();
                randomBitIndex -= MAX_RANDOM_BIT_INDEX;
            }
            alignment = randomBitIndex & 63;
            result <<= alignment;
            result |= (randomBits[randomBitIndex >>> 6] >>> (64 - alignment)) & ((1L << alignment) - 1);

            return result;
        }
    }

    @Override
    public RandomSource fork() {
        WorldgenCryptoRandom fork = new WorldgenCryptoRandom(0, 0, null, 0);

        System.arraycopy(Globals.worldSeed, 0, fork.worldSeed, 0, Globals.WORLD_SEED_LONGS);
        fork.message[0] = this.message[0];
        fork.message[1] = this.message[1];
        fork.message[2] = this.message[2];
        fork.message[3] = this.message[3];
        fork.randomBitIndex = this.randomBitIndex;
        fork.counter = this.counter;
        fork.nextLong();

        return fork;
    }

    @Override
    public int next(int bits) {
        return (int) getBits(bits);
    }

    @Override
    public void consumeCount(int count) {
        randomBitIndex += count;
        if (randomBitIndex >= MAX_RANDOM_BIT_INDEX * 2) {
            randomBitIndex -= MAX_RANDOM_BIT_INDEX;
            counter += randomBitIndex >>> LOG2_MAX_RANDOM_BIT_INDEX;
            randomBitIndex &= MAX_RANDOM_BIT_INDEX - 1;
            randomBitIndex += MAX_RANDOM_BIT_INDEX;
        }
    }

    @Override
    public int nextInt(int bound) {
        int bits = Mth.ceillog2(bound);
        int result;
        do {
            result = (int) getBits(bits);
        } while (result >= bound);

        return result;
    }

    @Override
    public long nextLong() {
        return getBits(64);
    }

    @Override
    public double nextDouble() {
        return getBits(53) * 0x1.0p-53;
    }

    @Override
    public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
        setSecureSeed(blockX, blockZ, Globals.Salt.POPULATION, 0);
        return ((long) blockX << 32) | ((long) blockZ & 0xffffffffL);
    }

    @Override
    public void setFeatureSeed(long populationSeed, int index, int step) {
        setSecureSeed((int) (populationSeed >> 32), (int) populationSeed, Globals.Salt.DECORATION, index + 10000L * step);
    }

    @Override
    public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        super.setLargeFeatureSeed(worldSeed, chunkX, chunkZ);
    }

    @Override
    public void setLargeFeatureWithSalt(long worldSeed, int regionX, int regionZ, int salt) {
        super.setLargeFeatureWithSalt(worldSeed, regionX, regionZ, salt);
    }

    public static RandomSource seedSlimeChunk(int chunkX, int chunkZ) {
        return new WorldgenCryptoRandom(chunkX, chunkZ, Globals.Salt.SLIME_CHUNK, 0);
    }
}
