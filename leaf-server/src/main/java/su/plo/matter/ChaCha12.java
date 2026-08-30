package su.plo.matter;

import java.util.Objects;

public final class ChaCha12 {
    public static final int KEY_BYTES = 32;
    private static final int CONSTANT_0 = 0x61707865;
    private static final int CONSTANT_1 = 0x3320646E;
    private static final int CONSTANT_2 = 0x79622D32;
    private static final int CONSTANT_3 = 0x6B206574;
    private static final int DOUBLE_ROUNDS = 6;

    @FunctionalInterface
    interface Seed128Factory<R> {
        R create(long seedLo, long seedHi);
    }

    private ChaCha12() {
    }

    public static PreparedKey prepare(final byte[] key) {
        return new PreparedKey(key);
    }

    public static final class PreparedKey {
        private final int key0;
        private final int key1;
        private final int key2;
        private final int key3;
        private final int key4;
        private final int key5;
        private final int key6;
        private final int key7;

        private PreparedKey(final byte[] key) {
            Objects.requireNonNull(key, "key");
            if (key.length != KEY_BYTES) {
                throw new IllegalArgumentException("ChaCha12 key must be exactly " + KEY_BYTES + " bytes");
            }

            this.key0 = readIntLittleEndian(key, 0);
            this.key1 = readIntLittleEndian(key, 4);
            this.key2 = readIntLittleEndian(key, 8);
            this.key3 = readIntLittleEndian(key, 12);
            this.key4 = readIntLittleEndian(key, 16);
            this.key5 = readIntLittleEndian(key, 20);
            this.key6 = readIntLittleEndian(key, 24);
            this.key7 = readIntLittleEndian(key, 28);
        }

        <R> R derive(final int nonce0, final int nonce1, final int nonce2, final Seed128Factory<R> factory) {
            Objects.requireNonNull(factory, "factory");
            int counter = 0;
            while (true) {
                R result = this.block(counter, nonce0, nonce1, nonce2, factory);
                if (result != null) {
                    return result;
                }
                if (counter == -1) {
                    throw new IllegalStateException("Unable to derive a non-zero ChaCha12 seed");
                }
                counter++;
            }
        }

        private <R> R block(final int counter, final int nonce0, final int nonce1, final int nonce2, final Seed128Factory<R> factory) {
            int x0 = CONSTANT_0;
            int x1 = CONSTANT_1;
            int x2 = CONSTANT_2;
            int x3 = CONSTANT_3;
            int x4 = this.key0;
            int x5 = this.key1;
            int x6 = this.key2;
            int x7 = this.key3;
            int x8 = this.key4;
            int x9 = this.key5;
            int x10 = this.key6;
            int x11 = this.key7;
            int x12 = counter;
            int x13 = nonce0;
            int x14 = nonce1;
            int x15 = nonce2;

            for (int round = 0; round < DOUBLE_ROUNDS; round++) {
                x0 += x4;
                x12 = Integer.rotateLeft(x12 ^ x0, 16);
                x8 += x12;
                x4 = Integer.rotateLeft(x4 ^ x8, 12);
                x0 += x4;
                x12 = Integer.rotateLeft(x12 ^ x0, 8);
                x8 += x12;
                x4 = Integer.rotateLeft(x4 ^ x8, 7);

                x1 += x5;
                x13 = Integer.rotateLeft(x13 ^ x1, 16);
                x9 += x13;
                x5 = Integer.rotateLeft(x5 ^ x9, 12);
                x1 += x5;
                x13 = Integer.rotateLeft(x13 ^ x1, 8);
                x9 += x13;
                x5 = Integer.rotateLeft(x5 ^ x9, 7);

                x2 += x6;
                x14 = Integer.rotateLeft(x14 ^ x2, 16);
                x10 += x14;
                x6 = Integer.rotateLeft(x6 ^ x10, 12);
                x2 += x6;
                x14 = Integer.rotateLeft(x14 ^ x2, 8);
                x10 += x14;
                x6 = Integer.rotateLeft(x6 ^ x10, 7);

                x3 += x7;
                x15 = Integer.rotateLeft(x15 ^ x3, 16);
                x11 += x15;
                x7 = Integer.rotateLeft(x7 ^ x11, 12);
                x3 += x7;
                x15 = Integer.rotateLeft(x15 ^ x3, 8);
                x11 += x15;
                x7 = Integer.rotateLeft(x7 ^ x11, 7);

                x0 += x5;
                x15 = Integer.rotateLeft(x15 ^ x0, 16);
                x10 += x15;
                x5 = Integer.rotateLeft(x5 ^ x10, 12);
                x0 += x5;
                x15 = Integer.rotateLeft(x15 ^ x0, 8);
                x10 += x15;
                x5 = Integer.rotateLeft(x5 ^ x10, 7);

                x1 += x6;
                x12 = Integer.rotateLeft(x12 ^ x1, 16);
                x11 += x12;
                x6 = Integer.rotateLeft(x6 ^ x11, 12);
                x1 += x6;
                x12 = Integer.rotateLeft(x12 ^ x1, 8);
                x11 += x12;
                x6 = Integer.rotateLeft(x6 ^ x11, 7);

                x2 += x7;
                x13 = Integer.rotateLeft(x13 ^ x2, 16);
                x8 += x13;
                x7 = Integer.rotateLeft(x7 ^ x8, 12);
                x2 += x7;
                x13 = Integer.rotateLeft(x13 ^ x2, 8);
                x8 += x13;
                x7 = Integer.rotateLeft(x7 ^ x8, 7);

                x3 += x4;
                x14 = Integer.rotateLeft(x14 ^ x3, 16);
                x9 += x14;
                x4 = Integer.rotateLeft(x4 ^ x9, 12);
                x3 += x4;
                x14 = Integer.rotateLeft(x14 ^ x3, 8);
                x9 += x14;
                x4 = Integer.rotateLeft(x4 ^ x9, 7);
            }

            long seedLo = packInts(x0 + CONSTANT_0, x1 + CONSTANT_1);
            long seedHi = packInts(x2 + CONSTANT_2, x3 + CONSTANT_3);
            if ((seedLo | seedHi) != 0L) {
                return factory.create(seedLo, seedHi);
            }

            seedLo = packInts(x4 + this.key0, x5 + this.key1);
            seedHi = packInts(x6 + this.key2, x7 + this.key3);
            if ((seedLo | seedHi) != 0L) {
                return factory.create(seedLo, seedHi);
            }

            seedLo = packInts(x8 + this.key4, x9 + this.key5);
            seedHi = packInts(x10 + this.key6, x11 + this.key7);
            if ((seedLo | seedHi) != 0L) {
                return factory.create(seedLo, seedHi);
            }

            seedLo = packInts(x12 + counter, x13 + nonce0);
            seedHi = packInts(x14 + nonce1, x15 + nonce2);
            return (seedLo | seedHi) == 0L ? null : factory.create(seedLo, seedHi);
        }
    }

    private static int readIntLittleEndian(final byte[] input, final int offset) {
        return (input[offset] & 0xFF)
            | (input[offset + 1] & 0xFF) << 8
            | (input[offset + 2] & 0xFF) << 16
            | (input[offset + 3] & 0xFF) << 24;
    }

    private static long packInts(final int low, final int high) {
        return Integer.toUnsignedLong(low) | Integer.toUnsignedLong(high) << Integer.SIZE;
    }
}
