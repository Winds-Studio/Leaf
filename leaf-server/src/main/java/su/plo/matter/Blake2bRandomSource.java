package su.plo.matter;

import java.util.Objects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

final class Blake2bRandomSource implements BitRandomSource {
    private static final int BLOCK_BITS = Blake2b.OUTPUT_BYTES * Byte.SIZE;
    private final Blake2b.PreparedKey baseKey;
    private Blake2b.PreparedKey streamKey;
    private final byte[] block = new byte[Blake2b.OUTPUT_BYTES];
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);
    private long counter;
    private boolean exhausted;
    private int bitIndex = BLOCK_BITS;

    Blake2bRandomSource(final Blake2b.PreparedKey key) {
        this.baseKey = Objects.requireNonNull(key, "key");
        this.streamKey = key;
    }

    @Override
    public int next(final int bits) {
        if (bits < 0 || bits > Integer.SIZE) {
            throw new IllegalArgumentException("Bit count must be between 0 and " + Integer.SIZE + ": " + bits);
        }

        int remaining = bits;
        int result = 0;
        while (remaining > 0) {
            this.ensureBlock();
            int byteIndex = this.bitIndex >>> 3;
            int bitOffset = this.bitIndex & 7;
            int take = Math.min(remaining, Byte.SIZE - bitOffset);
            int shift = Byte.SIZE - bitOffset - take;
            int value = (this.block[byteIndex] & 0xFF) >>> shift & (1 << take) - 1;
            result = result << take | value;
            this.bitIndex += take;
            remaining -= take;
        }
        return result;
    }

    @Override
    public RandomSource fork() {
        byte[] forkSeed = this.consumeMasterSeed();
        return new Blake2bRandomSource(TerrainHashing.derivePrepared(this.streamKey, TerrainHashing.FORK, forkSeed));
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        byte[] forkSeed = this.consumeMasterSeed();
        return new Blake2bPositionalRandomFactory(TerrainHashing.derivePrepared(this.streamKey, TerrainHashing.POSITIONAL_FORK, forkSeed));
    }

    @Override
    public void setSeed(final long seed) {
        this.streamKey = TerrainHashing.derivePreparedLong(this.baseKey, TerrainHashing.SET_SEED, seed);
        this.counter = 0L;
        this.exhausted = false;
        this.bitIndex = BLOCK_BITS;
        this.gaussianSource.reset();
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    @Override
    public long nextLong() {
        return (long) this.next(Integer.SIZE) << Integer.SIZE | Integer.toUnsignedLong(this.next(Integer.SIZE));
    }

    @Override
    public void consumeCount(final int rounds) {
        if (rounds <= 0) {
            return;
        }

        long bits = (long) rounds * Integer.SIZE;
        if (this.bitIndex < BLOCK_BITS) {
            int available = BLOCK_BITS - this.bitIndex;
            int consumed = (int) Math.min(bits, available);
            this.bitIndex += consumed;
            bits -= consumed;
        }

        if (bits >= BLOCK_BITS) {
            long blocks = bits / BLOCK_BITS;
            this.skipBlocks(blocks);
            bits %= BLOCK_BITS;
            this.bitIndex = BLOCK_BITS;
        }

        if (bits > 0L) {
            this.ensureBlock();
            this.bitIndex += (int) bits;
        }
    }

    private void ensureBlock() {
        if (this.bitIndex < BLOCK_BITS) {
            return;
        }
        if (this.exhausted) {
            throw new IllegalStateException("BLAKE2b random source counter exhausted");
        }

        long blockCounter = this.counter;
        if (blockCounter == -1L) {
            this.exhausted = true;
        } else {
            this.counter = blockCounter + 1L;
        }
        TerrainHashing.deriveBlock(this.streamKey, blockCounter, this.block);
        this.bitIndex = 0;
    }

    private void skipBlocks(final long blocks) {
        if (blocks == 0L) {
            return;
        }
        if (this.exhausted) {
            throw new IllegalStateException("BLAKE2b random source counter exhausted");
        }

        long nextCounter = this.counter + blocks;
        if (Long.compareUnsigned(nextCounter, this.counter) < 0) {
            if (nextCounter != 0L) {
                throw new IllegalStateException("BLAKE2b random source counter exhausted");
            }
            this.exhausted = true;
        }
        this.counter = nextCounter;
    }

    private byte[] consumeMasterSeed() {
        byte[] seed = new byte[Blake2b.OUTPUT_BYTES];
        for (int i = 0; i < seed.length; i += Integer.BYTES) {
            int value = this.next(Integer.SIZE);
            seed[i] = (byte) (value >>> 24);
            seed[i + 1] = (byte) (value >>> 16);
            seed[i + 2] = (byte) (value >>> 8);
            seed[i + 3] = (byte) value;
        }
        return seed;
    }

    private static final class Blake2bPositionalRandomFactory implements PositionalRandomFactory {
        private final Blake2b.PreparedKey key;

        private Blake2bPositionalRandomFactory(final Blake2b.PreparedKey key) {
            this.key = key;
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return new Blake2bRandomSource(TerrainHashing.derivePreparedPosition(this.key, TerrainHashing.POSITIONAL_AT, x, y, z));
        }

        @Override
        public RandomSource fromHashOf(final String name) {
            return new Blake2bRandomSource(TerrainHashing.derivePrepared(this.key, TerrainHashing.POSITIONAL_HASH, TerrainHashing.stringToBytes(name)));
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return new Blake2bRandomSource(TerrainHashing.derivePreparedLong(this.key, TerrainHashing.POSITIONAL_SEED, seed));
        }

        @Override
        public void parityConfigString(final StringBuilder sb) {
            sb.append("Blake2bPositionalRandomFactory");
        }
    }
}
