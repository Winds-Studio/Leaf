package org.dreeam.leaf.util.math.random;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.jspecify.annotations.NullMarked;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@NullMarked
public final class FasterRandomSource implements BitRandomSource {
    private static final RandomGeneratorFactory<RandomGenerator> RANDOM_GENERATOR_FACTORY = RandomGeneratorFactory.of("Xoroshiro128PlusPlus");
    private RandomGenerator delegate;
    public static final FasterRandomSource SHARED_INSTANCE = new FasterRandomSource(RandomSupport.generateUniqueSeed());

    public FasterRandomSource(long seed) {
        this.delegate = RANDOM_GENERATOR_FACTORY.create(seed);
    }

    @Override
    public RandomSource fork() {
        return new FasterRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new FasterRandomSourcePositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.delegate = RANDOM_GENERATOR_FACTORY.create(seed);
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.delegate.nextLong();
        }
    }

    @Override
    public int next(int bits) {
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
        return delegate.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return delegate.nextInt(bound);
    }

    @Override
    public int nextInt(int origin, int bound) {
        return delegate.nextInt(origin, bound);
    }

    @Override
    public long nextLong() {
        return delegate.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return delegate.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return delegate.nextFloat();
    }

    @Override
    public double nextDouble() {
        return delegate.nextDouble();
    }

    @Override
    public double nextGaussian() {
        return delegate.nextGaussian();
    }
}
