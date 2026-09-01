package su.plo.matter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

@NullMarked
public class WorldgenCryptoRandom extends WorldgenRandom {

    // hash the world seed to guard against badly chosen world seeds
    private static final long[] HASHED_ZERO_SEED = Hashing.hashWorldSeed(new long[Globals.WORLD_SEED_LONGS]);
    private static final ThreadLocal<long[]> LAST_SEEN_WORLD_SEED = ThreadLocal.withInitial(() -> new long[Globals.WORLD_SEED_LONGS]);
    private static final ThreadLocal<long[]> HASHED_WORLD_SEED = ThreadLocal.withInitial(() -> HASHED_ZERO_SEED);
    private static final long POSITIONAL_AT_DOMAIN = 1L;
    private static final long POSITIONAL_HASH_DOMAIN = 2L;
    private static final long POSITIONAL_SEED_DOMAIN = 3L;

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

    private WorldgenCryptoRandom(long[] worldSeed, long input0, long input1, long input2, long domain) {
        this(0, 0, null, 0);
        System.arraycopy(worldSeed, 0, this.worldSeed, 0, this.worldSeed.length);
        this.message[0] = input0;
        this.message[1] = input1;
        this.message[2] = input2;
        this.message[3] = this.counter = 0;
        this.message[4] = domain;
        this.randomBitIndex = MAX_RANDOM_BIT_INDEX;
    }

    // Slime chunk checks can run without a worldgen ThreadLocal context, so snapshot the target level directly.
    private WorldgenCryptoRandom(ServerLevel level, int x, int z, Globals.Salt typeSalt, long salt) {
        this(0, 0, null, 0);
        Globals.copyWorldSeed(level, this.worldSeed);
        this.setSecureSeed(x, z, typeSalt, salt, Objects.requireNonNull(level.matter$dimensionSeed));
    }

    public void setSecureSeed(int x, int z, Globals.Salt typeSalt, long salt) {
        Globals.copyWorldSeed(this.worldSeed);
        this.setSecureSeed(x, z, typeSalt, salt, Globals.dimensionSeed());
    }

    private void setSecureSeed(int x, int z, Globals.Salt typeSalt, long salt, Globals.DimensionSeed dimensionSeed) {
        message[0] = ((long) x << 32) | ((long) z & 0xffffffffL);
        message[1] = ((long) dimensionSeed.legacyId() << 32) | (salt & 0xffffffffL);
        message[2] = typeSalt.ordinal();
        message[3] = counter = 0;
        message[4] = 0;
        message[5] = dimensionSeed.seedLo();
        message[6] = dimensionSeed.seedHi();
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

    private static long lowMask(int bits) {
        return bits == Long.SIZE ? -1L : (1L << bits) - 1L;
    }

    private long getBits(int count) {
        if (count < 0 || count > Long.SIZE) {
            throw new IllegalArgumentException("Bit count must be between 0 and " + Long.SIZE + ": " + count);
        }

        if (randomBitIndex >= MAX_RANDOM_BIT_INDEX) {
            moreRandomBits();
            randomBitIndex -= MAX_RANDOM_BIT_INDEX;
        }

        int alignment = randomBitIndex & 63;
        int availableBits = Long.SIZE - alignment;
        long result;
        if (count <= availableBits) {
            result = (randomBits[randomBitIndex >>> 6] >>> alignment) & lowMask(count);
            randomBitIndex += count;
        } else {
            result = (randomBits[randomBitIndex >>> 6] >>> alignment) & lowMask(availableBits);
            randomBitIndex += availableBits;
            if (randomBitIndex >= MAX_RANDOM_BIT_INDEX) {
                moreRandomBits();
                randomBitIndex -= MAX_RANDOM_BIT_INDEX;
            }

            int remainingBits = count - availableBits;
            result |= (randomBits[randomBitIndex >>> 6] & lowMask(remainingBits)) << availableBits;
            randomBitIndex += remainingBits;

        }
        return result;
    }

    @Override
    public RandomSource fork() {
        WorldgenCryptoRandom fork = new WorldgenCryptoRandom(0, 0, null, 0);

        System.arraycopy(this.worldSeed, 0, fork.worldSeed, 0, this.worldSeed.length);
        System.arraycopy(this.randomBits, 0, fork.randomBits, 0, this.randomBits.length);
        System.arraycopy(this.message, 0, fork.message, 0, this.message.length);
        fork.randomBitIndex = this.randomBitIndex;
        fork.counter = this.counter;
        fork.nextLong();

        return fork;
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        long[] factorySeed = new long[Globals.WORLD_SEED_LONGS];
        for (int i = 0; i < factorySeed.length; i++) {
            factorySeed[i] = this.nextLong();
        }
        return new CryptoPositionalRandomFactory(factorySeed);
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

    public static RandomSource seedSlimeChunk(ServerLevel level, int chunkX, int chunkZ) {
        return new WorldgenCryptoRandom(level, chunkX, chunkZ, Globals.Salt.SLIME_CHUNK, 0);
    }

    private static final class CryptoPositionalRandomFactory implements PositionalRandomFactory {
        private final long[] worldSeed;

        private CryptoPositionalRandomFactory(long[] worldSeed) {
            this.worldSeed = worldSeed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            return new WorldgenCryptoRandom(this.worldSeed, x, y, z, POSITIONAL_AT_DOMAIN);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit seed = RandomSupport.seedFromHashOf(name);
            return new WorldgenCryptoRandom(this.worldSeed, seed.seedLo(), seed.seedHi(), 0L, POSITIONAL_HASH_DOMAIN);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new WorldgenCryptoRandom(this.worldSeed, seed, 0L, 0L, POSITIONAL_SEED_DOMAIN);
        }

        @Override
        public void parityConfigString(StringBuilder sb) {
            sb.append("CryptoPositionalRandomFactory");
        }
    }
}
