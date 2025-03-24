package org.dreeam.leaf.util.math.random;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.dreeam.leaf.config.modules.opt.FastRNG;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;


public class FasterRandomSource implements BitRandomSource {

    private static final int INT_BITS = 48;
    private static final long SEED_MASK = 0xFFFFFFFFFFFFL;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private static final RandomGeneratorFactory<RandomGenerator> RANDOM_GENERATOR_FACTORY = RandomGeneratorFactory.of(FastRNG.randomGenerator);
    private static final boolean isSplittableGenerator = RANDOM_GENERATOR_FACTORY.isSplittable();
    private long seed;
    private boolean useDirectImpl;
    private RandomGenerator randomGenerator;
    public static final FasterRandomSource SHARED_INSTANCE = new FasterRandomSource(ThreadLocalRandom.current().nextLong());

    public FasterRandomSource(long seed) {
        this.seed = seed;
        this.randomGenerator = RANDOM_GENERATOR_FACTORY.create(seed);
        this.useDirectImpl = FastRNG.useDirectImpl; // Get the value from config
    }

    private FasterRandomSource(long seed, RandomGenerator.SplittableGenerator randomGenerator) {
        this.seed = seed;
        this.randomGenerator = randomGenerator;
        this.useDirectImpl = FastRNG.useDirectImpl;
    }

    @Override
    public final RandomSource fork() {
        if (isSplittableGenerator) {
            return new FasterRandomSource(seed, ((RandomGenerator.SplittableGenerator) this.randomGenerator).split());
        }
        return new FasterRandomSource(this.nextLong());
    }

    @Override
    public final PositionalRandomFactory forkPositional() {
        return new FasterRandomSourcePositionalRandomFactory(this.seed);
    }

    @Override
    public final void setSeed(long seed) {
        this.seed = seed;
        this.randomGenerator = RANDOM_GENERATOR_FACTORY.create(seed);
    }

    @Override
    public final int next(int bits) {
        if (useDirectImpl) {
            // Direct
            return (int) ((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> (INT_BITS - bits));
        } else {
            // old
            return (int) ((seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> (INT_BITS - bits));
        }
    }

    public static class FasterRandomSourcePositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public FasterRandomSourcePositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = Mth.getSeed(x, y, z);
            long m = l ^ this.seed;
            return new FasterRandomSource(m);
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            int i = seed.hashCode();
            return new FasterRandomSource((long) i ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new FasterRandomSource(seed);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("FasterRandomSourcePositionalRandomFactory{").append(this.seed).append("}");
        }
    }

    @Override
    public final int nextInt() {
        if (useDirectImpl) {
            return (int) (((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> 16) ^
                ((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> 32));
        } else {
            return randomGenerator.nextInt();
        }
    }

    @Override
    public final int nextInt(int bound) {
        if (useDirectImpl && bound > 0) {
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits, val;
            do {
                bits = next(31);
                val = bits % bound;
            } while (bits - val + (bound - 1) < 0);
            return val;
        } else {
            return randomGenerator.nextInt(bound);
        }
    }

    @Override
    public final long nextLong() {
        if (useDirectImpl) {
            return ((long) next(32) << 32) + next(32);
        } else {
            return randomGenerator.nextLong();
        }
    }

    @Override
    public final boolean nextBoolean() {
        if (useDirectImpl) {
            return next(1) != 0;
        } else {
            return randomGenerator.nextBoolean();
        }
    }

    @Override
    public final float nextFloat() {
        if (useDirectImpl) {
            return next(24) / ((float) (1 << 24));
        } else {
            return randomGenerator.nextFloat();
        }
    }

    @Override
    public final double nextDouble() {
        if (useDirectImpl) {
            return (((long) next(26) << 27) + next(27)) / (double) (1L << 53);
        } else {
            return randomGenerator.nextDouble();
        }
    }

    @Override
    public final double nextGaussian() {
        // delegate Gaussian distribution to RandomGenerator
        // as direct implementation would be complex (i aint doin allat)
        return randomGenerator.nextGaussian();
    }
}
