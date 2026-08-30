package su.plo.matter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.crypto.Mac;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

final class HmacSha256RandomSource implements BitRandomSource {
    private static final int BLOCK_BITS = 256;
    private final byte[] baseKey;
    private byte[] streamKey;
    private final byte[] block = new byte[TerrainCrypto.MASTER_SEED_BYTES];
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);
    private Mac blockMac;
    private long counter;
    private boolean exhausted;
    private int bitIndex = BLOCK_BITS;

    HmacSha256RandomSource(final byte[] key) {
        if (key.length != TerrainCrypto.MASTER_SEED_BYTES) {
            throw new IllegalArgumentException("HMAC random source key must be exactly " + TerrainCrypto.MASTER_SEED_BYTES + " bytes");
        }
        this.baseKey = key.clone();
        this.streamKey = key.clone();
        this.blockMac = TerrainHashing.createMac(this.streamKey);
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
        return new HmacSha256RandomSource(
            TerrainHashing.derive(this.streamKey, TerrainHashing.FORK, this.consumeMasterSeed())
        );
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new HmacPositionalRandomFactory(
            TerrainHashing.derive(this.streamKey, TerrainHashing.POSITIONAL_FORK, this.consumeMasterSeed())
        );
    }

    @Override
    public void setSeed(final long seed) {
        this.streamKey = TerrainHashing.derive(this.baseKey, TerrainHashing.SET_SEED, TerrainHashing.longContext(seed));
        this.blockMac = TerrainHashing.createMac(this.streamKey);
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
            throw new IllegalStateException("HMAC random source counter exhausted");
        }

        long blockCounter = this.counter;
        if (blockCounter == -1L) {
            this.exhausted = true;
        } else {
            this.counter = blockCounter + 1L;
        }
        TerrainHashing.deriveBlock(this.blockMac, blockCounter, this.block);
        this.bitIndex = 0;
    }

    private void skipBlocks(final long blocks) {
        if (blocks == 0L) {
            return;
        }
        if (this.exhausted) {
            throw new IllegalStateException("HMAC random source counter exhausted");
        }

        long nextCounter = this.counter + blocks;
        if (Long.compareUnsigned(nextCounter, this.counter) < 0) {
            if (nextCounter != 0L) {
                throw new IllegalStateException("HMAC random source counter exhausted");
            }
            this.exhausted = true;
        }
        this.counter = nextCounter;
    }

    private byte[] consumeMasterSeed() {
        ByteBuffer buffer = ByteBuffer.allocate(TerrainCrypto.MASTER_SEED_BYTES).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < TerrainCrypto.MASTER_SEED_BYTES / Integer.BYTES; i++) {
            buffer.putInt(this.next(Integer.SIZE));
        }
        return buffer.array();
    }

    private static final class HmacPositionalRandomFactory implements PositionalRandomFactory {
        private final byte[] key;

        private HmacPositionalRandomFactory(final byte[] key) {
            this.key = key.clone();
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return new HmacSha256RandomSource(
                TerrainHashing.derive(this.key, TerrainHashing.POSITIONAL_AT, TerrainHashing.positionContext(x, y, z))
            );
        }

        @Override
        public RandomSource fromHashOf(final String name) {
            return new HmacSha256RandomSource(
                TerrainHashing.derive(this.key, TerrainHashing.POSITIONAL_HASH, TerrainHashing.stringContext(name))
            );
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return new HmacSha256RandomSource(
                TerrainHashing.derive(this.key, TerrainHashing.POSITIONAL_SEED, TerrainHashing.longContext(seed))
            );
        }

        @Override
        public void parityConfigString(final StringBuilder sb) {
            sb.append("HmacSha256PositionalRandomFactory");
        }
    }
}
