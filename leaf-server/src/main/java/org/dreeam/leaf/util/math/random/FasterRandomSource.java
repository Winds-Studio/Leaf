package org.dreeam.leaf.util.math.random;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.dreeam.leaf.config.modules.opt.FastRNG;
import org.jspecify.annotations.NullMarked;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@NullMarked
public final class FasterRandomSource implements BitRandomSource {

    private static final int INT_BITS = 48;
    private static final long SEED_MASK = 0xFFFFFFFFFFFFL;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private static final RandomGeneratorFactory<RandomGenerator> RANDOM_GENERATOR_FACTORY = RandomGeneratorFactory.of(FastRNG.randomGenerator);
    private RandomGenerator delegate;
    @Deprecated
    private long seed;
    public static final FasterRandomSource SHARED_INSTANCE = new FasterRandomSource(RandomSupport.generateUniqueSeed());
    @Deprecated
    private static final boolean useDirectImpl = FastRNG.useDirectImpl;

    public FasterRandomSource(long seed) {
        this.seed = seed;
        this.delegate = RANDOM_GENERATOR_FACTORY.create(seed);
    }

    private FasterRandomSource(RandomGenerator.SplittableGenerator randomGenerator) {
        this.seed = randomGenerator.nextLong();
        this.delegate = randomGenerator;
    }

    @Override
    public RandomSource fork() {
        if (useDirectImpl) {
            return new FasterRandomSource(this.nextLong());
        }
        return RANDOM_GENERATOR_FACTORY.isSplittable()
            ? new FasterRandomSource(((RandomGenerator.SplittableGenerator) this.delegate).split())
            : new FasterRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new FasterRandomSourcePositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = seed;
        this.delegate = RANDOM_GENERATOR_FACTORY.create(seed);
    }

    @Override
    public int next(int bits) {
        if (useDirectImpl) {
            return (int) ((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> (INT_BITS - bits));
        }
        return (int) (nextLong() >>> (64 - bits));
    }

    private static final class FasterRandomSourcePositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public FasterRandomSourcePositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            return new FasterRandomSource(Mth.getSeed(x, y, z) ^ this.seed);
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
    public int nextInt() {
        if (useDirectImpl) {
            return (int) (((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> 16) ^
                ((seed = seed * MULTIPLIER + INCREMENT & SEED_MASK) >>> 32));
        }
        return delegate.nextInt();
    }

    @Override
    public int nextInt(int bound) {
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
        }
        return delegate.nextInt(bound);
    }

    @Override
    public int nextInt(int origin, int bound) {
        if (useDirectImpl && bound > 0) {
            return origin + this.nextInt(bound - origin);
        }
        return delegate.nextInt(origin, bound);
    }

    @Override
    public long nextLong() {
        if (useDirectImpl) {
            return ((long) next(32) << 32) + next(32);
        }
        return delegate.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        if (useDirectImpl) {
            return next(1) != 0;
        }
        return delegate.nextBoolean();
    }

    @Override
    public float nextFloat() {
        if (useDirectImpl) {
            return next(24) / ((float) (1 << 24));
        }
        return delegate.nextFloat();
    }

    @Override
    public double nextDouble() {
        if (useDirectImpl) {
            return (((long) next(26) << 27) + next(27)) / (double) (1L << 53);
        }
        return delegate.nextDouble();
    }

    @Override
    public double nextGaussian() {
        // delegate Gaussian distribution to RandomGenerator
        // as direct implementation would be complex (i aint doin allat)
        return delegate.nextGaussian();
    }
}
