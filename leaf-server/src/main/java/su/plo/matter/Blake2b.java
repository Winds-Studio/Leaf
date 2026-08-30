package su.plo.matter;

import java.util.Objects;

public final class Blake2b {
    public static final int KEY_BYTES = 32;
    public static final int OUTPUT_BYTES = 32;
    private static final int BLOCK_BYTES = 128;
    private static final int FIXED_16_BYTES = 16;
    private static final int FIXED_20_BYTES = 20;
    private static final long PARAMETER_BLOCK = 0x01012020L;
    private static final long IV_0 = 0x6A09E667F3BCC908L;
    private static final long IV_1 = 0xBB67AE8584CAA73BL;
    private static final long IV_2 = 0x3C6EF372FE94F82BL;
    private static final long IV_3 = 0xA54FF53A5F1D36F1L;
    private static final long IV_4 = 0x510E527FADE682D1L;
    private static final long IV_5 = 0x9B05688C2B3E6C1FL;
    private static final long IV_6 = 0x1F83D9ABFB41BD6BL;
    private static final long IV_7 = 0x5BE0CD19137E2179L;

    @FunctionalInterface
    interface Seed128Factory<R> {
        R create(long seedLo, long seedHi);
    }

    private Blake2b() {
    }

    public static PreparedKey prepare(final byte[] key) {
        return new PreparedKey(key);
    }

    public static byte[] hash(final byte[] key, final byte[] input) {
        Objects.requireNonNull(input, "input");
        byte[] output = new byte[OUTPUT_BYTES];
        hash(key, null, input, 0, input.length, output, 0);
        return output;
    }

    public static void hash(final byte[] key, final byte[] input, final byte[] output) {
        Objects.requireNonNull(input, "input");
        hash(key, null, input, 0, input.length, output, 0);
    }

    public static void hash(final byte[] key, final byte[] input, final int inputOffset, final int inputLength, final byte[] output, final int outputOffset) {
        hash(key, null, input, inputOffset, inputLength, output, outputOffset);
    }

    private static void hash(final byte[] key, final PreparedKey preparedKey, final byte[] input, final int inputOffset, final int inputLength, final byte[] output, final int outputOffset) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        if (preparedKey == null) {
            Objects.requireNonNull(key, "key");
            if (key.length != KEY_BYTES) {
                throw new IllegalArgumentException("BLAKE2b-256 key must be exactly " + KEY_BYTES + " bytes");
            }
        }
        Objects.checkFromIndexSize(inputOffset, inputLength, input.length);
        Objects.checkFromIndexSize(outputOffset, OUTPUT_BYTES, output.length);
        if (preparedKey != null && inputLength == 0) {
            preparedKey.writeEmptyHash(output, outputOffset);
            return;
        }

        hashScalar(key, preparedKey, input, inputOffset, inputLength, 0L, 0L, 0L, output, outputOffset, null);
    }

    private static <R> R hashScalar(final byte[] key, final PreparedKey preparedKey, final byte[] input, final int inputOffset, final int inputLength, final long fixedM0, final long fixedM1, final long fixedM2, final byte[] output, final int outputOffset, final Seed128Factory<R> factory) {
        final boolean fixedInput = input == null;
        long h0 = preparedKey == null ? IV_0 ^ PARAMETER_BLOCK : preparedKey.h0;
        long h1 = preparedKey == null ? IV_1 : preparedKey.h1;
        long h2 = preparedKey == null ? IV_2 : preparedKey.h2;
        long h3 = preparedKey == null ? IV_3 : preparedKey.h3;
        long h4 = preparedKey == null ? IV_4 : preparedKey.h4;
        long h5 = preparedKey == null ? IV_5 : preparedKey.h5;
        long h6 = preparedKey == null ? IV_6 : preparedKey.h6;
        long h7 = preparedKey == null ? IV_7 : preparedKey.h7;
        long t0 = preparedKey == null ? 0L : BLOCK_BYTES;
        long t1 = 0L;
        int offset = inputOffset;
        int remaining = inputLength;
        boolean keyBlock = preparedKey == null;

        while (true) {
            final int blockLength;
            final boolean last;
            final long m0;
            final long m1;
            final long m2;
            final long m3;
            final long m4;
            final long m5;
            final long m6;
            final long m7;
            final long m8;
            final long m9;
            final long m10;
            final long m11;
            final long m12;
            final long m13;
            final long m14;
            final long m15;

            if (keyBlock) {
                blockLength = BLOCK_BYTES;
                last = remaining == 0;
                m0 = readLongLittleEndian(key, 0);
                m1 = readLongLittleEndian(key, 8);
                m2 = readLongLittleEndian(key, 16);
                m3 = readLongLittleEndian(key, 24);
                m4 = 0L;
                m5 = 0L;
                m6 = 0L;
                m7 = 0L;
                m8 = 0L;
                m9 = 0L;
                m10 = 0L;
                m11 = 0L;
                m12 = 0L;
                m13 = 0L;
                m14 = 0L;
                m15 = 0L;
                keyBlock = false;
            } else if (fixedInput) {
                blockLength = inputLength;
                last = true;
                m0 = fixedM0;
                m1 = fixedM1;
                m2 = inputLength == FIXED_20_BYTES ? fixedM2 & 0xFFFFFFFFL : 0L;
                m3 = 0L;
                m4 = 0L;
                m5 = 0L;
                m6 = 0L;
                m7 = 0L;
                m8 = 0L;
                m9 = 0L;
                m10 = 0L;
                m11 = 0L;
                m12 = 0L;
                m13 = 0L;
                m14 = 0L;
                m15 = 0L;
                remaining = 0;
            } else {
                blockLength = Math.min(remaining, BLOCK_BYTES);
                last = remaining <= BLOCK_BYTES;
                m0 = readBlockWord(input, offset, blockLength, 0);
                m1 = readBlockWord(input, offset, blockLength, 8);
                m2 = readBlockWord(input, offset, blockLength, 16);
                m3 = readBlockWord(input, offset, blockLength, 24);
                m4 = readBlockWord(input, offset, blockLength, 32);
                m5 = readBlockWord(input, offset, blockLength, 40);
                m6 = readBlockWord(input, offset, blockLength, 48);
                m7 = readBlockWord(input, offset, blockLength, 56);
                m8 = readBlockWord(input, offset, blockLength, 64);
                m9 = readBlockWord(input, offset, blockLength, 72);
                m10 = readBlockWord(input, offset, blockLength, 80);
                m11 = readBlockWord(input, offset, blockLength, 88);
                m12 = readBlockWord(input, offset, blockLength, 96);
                m13 = readBlockWord(input, offset, blockLength, 104);
                m14 = readBlockWord(input, offset, blockLength, 112);
                m15 = readBlockWord(input, offset, blockLength, 120);
                offset += blockLength;
                remaining -= blockLength;
            }

            long previousT0 = t0;
            t0 += blockLength;
            if (Long.compareUnsigned(t0, previousT0) < 0) {
                t1++;
            }

            long v0 = h0;
            long v1 = h1;
            long v2 = h2;
            long v3 = h3;
            long v4 = h4;
            long v5 = h5;
            long v6 = h6;
            long v7 = h7;
            long v8 = IV_0;
            long v9 = IV_1;
            long v10 = IV_2;
            long v11 = IV_3;
            long v12 = IV_4 ^ t0;
            long v13 = IV_5 ^ t1;
            long v14 = IV_6 ^ (last ? -1L : 0L);
            long v15 = IV_7;

            for (int round = 0; round < 12; round++) {
                final long s0;
                final long s1;
                final long s2;
                final long s3;
                final long s4;
                final long s5;
                final long s6;
                final long s7;
                final long s8;
                final long s9;
                final long s10;
                final long s11;
                final long s12;
                final long s13;
                final long s14;
                final long s15;

                switch (round) {
                    case 0, 10 -> {
                        s0 = m0;
                        s1 = m1;
                        s2 = m2;
                        s3 = m3;
                        s4 = m4;
                        s5 = m5;
                        s6 = m6;
                        s7 = m7;
                        s8 = m8;
                        s9 = m9;
                        s10 = m10;
                        s11 = m11;
                        s12 = m12;
                        s13 = m13;
                        s14 = m14;
                        s15 = m15;
                    }
                    case 1, 11 -> {
                        s0 = m14;
                        s1 = m10;
                        s2 = m4;
                        s3 = m8;
                        s4 = m9;
                        s5 = m15;
                        s6 = m13;
                        s7 = m6;
                        s8 = m1;
                        s9 = m12;
                        s10 = m0;
                        s11 = m2;
                        s12 = m11;
                        s13 = m7;
                        s14 = m5;
                        s15 = m3;
                    }
                    case 2 -> {
                        s0 = m11;
                        s1 = m8;
                        s2 = m12;
                        s3 = m0;
                        s4 = m5;
                        s5 = m2;
                        s6 = m15;
                        s7 = m13;
                        s8 = m10;
                        s9 = m14;
                        s10 = m3;
                        s11 = m6;
                        s12 = m7;
                        s13 = m1;
                        s14 = m9;
                        s15 = m4;
                    }
                    case 3 -> {
                        s0 = m7;
                        s1 = m9;
                        s2 = m3;
                        s3 = m1;
                        s4 = m13;
                        s5 = m12;
                        s6 = m11;
                        s7 = m14;
                        s8 = m2;
                        s9 = m6;
                        s10 = m5;
                        s11 = m10;
                        s12 = m4;
                        s13 = m0;
                        s14 = m15;
                        s15 = m8;
                    }
                    case 4 -> {
                        s0 = m9;
                        s1 = m0;
                        s2 = m5;
                        s3 = m7;
                        s4 = m2;
                        s5 = m4;
                        s6 = m10;
                        s7 = m15;
                        s8 = m14;
                        s9 = m1;
                        s10 = m11;
                        s11 = m12;
                        s12 = m6;
                        s13 = m8;
                        s14 = m3;
                        s15 = m13;
                    }
                    case 5 -> {
                        s0 = m2;
                        s1 = m12;
                        s2 = m6;
                        s3 = m10;
                        s4 = m0;
                        s5 = m11;
                        s6 = m8;
                        s7 = m3;
                        s8 = m4;
                        s9 = m13;
                        s10 = m7;
                        s11 = m5;
                        s12 = m15;
                        s13 = m14;
                        s14 = m1;
                        s15 = m9;
                    }
                    case 6 -> {
                        s0 = m12;
                        s1 = m5;
                        s2 = m1;
                        s3 = m15;
                        s4 = m14;
                        s5 = m13;
                        s6 = m4;
                        s7 = m10;
                        s8 = m0;
                        s9 = m7;
                        s10 = m6;
                        s11 = m3;
                        s12 = m9;
                        s13 = m2;
                        s14 = m8;
                        s15 = m11;
                    }
                    case 7 -> {
                        s0 = m13;
                        s1 = m11;
                        s2 = m7;
                        s3 = m14;
                        s4 = m12;
                        s5 = m1;
                        s6 = m3;
                        s7 = m9;
                        s8 = m5;
                        s9 = m0;
                        s10 = m15;
                        s11 = m4;
                        s12 = m8;
                        s13 = m6;
                        s14 = m2;
                        s15 = m10;
                    }
                    case 8 -> {
                        s0 = m6;
                        s1 = m15;
                        s2 = m14;
                        s3 = m9;
                        s4 = m11;
                        s5 = m3;
                        s6 = m0;
                        s7 = m8;
                        s8 = m12;
                        s9 = m2;
                        s10 = m13;
                        s11 = m7;
                        s12 = m1;
                        s13 = m4;
                        s14 = m10;
                        s15 = m5;
                    }
                    case 9 -> {
                        s0 = m10;
                        s1 = m2;
                        s2 = m8;
                        s3 = m4;
                        s4 = m7;
                        s5 = m6;
                        s6 = m1;
                        s7 = m5;
                        s8 = m15;
                        s9 = m11;
                        s10 = m9;
                        s11 = m14;
                        s12 = m3;
                        s13 = m12;
                        s14 = m13;
                        s15 = m0;
                    }
                    default -> throw new AssertionError("Invalid BLAKE2b round: " + round);
                }

                v0 = v0 + v4 + s0;
                v12 = Long.rotateRight(v12 ^ v0, 32);
                v8 += v12;
                v4 = Long.rotateRight(v4 ^ v8, 24);
                v0 = v0 + v4 + s1;
                v12 = Long.rotateRight(v12 ^ v0, 16);
                v8 += v12;
                v4 = Long.rotateRight(v4 ^ v8, 63);

                v1 = v1 + v5 + s2;
                v13 = Long.rotateRight(v13 ^ v1, 32);
                v9 += v13;
                v5 = Long.rotateRight(v5 ^ v9, 24);
                v1 = v1 + v5 + s3;
                v13 = Long.rotateRight(v13 ^ v1, 16);
                v9 += v13;
                v5 = Long.rotateRight(v5 ^ v9, 63);

                v2 = v2 + v6 + s4;
                v14 = Long.rotateRight(v14 ^ v2, 32);
                v10 += v14;
                v6 = Long.rotateRight(v6 ^ v10, 24);
                v2 = v2 + v6 + s5;
                v14 = Long.rotateRight(v14 ^ v2, 16);
                v10 += v14;
                v6 = Long.rotateRight(v6 ^ v10, 63);

                v3 = v3 + v7 + s6;
                v15 = Long.rotateRight(v15 ^ v3, 32);
                v11 += v15;
                v7 = Long.rotateRight(v7 ^ v11, 24);
                v3 = v3 + v7 + s7;
                v15 = Long.rotateRight(v15 ^ v3, 16);
                v11 += v15;
                v7 = Long.rotateRight(v7 ^ v11, 63);

                v0 = v0 + v5 + s8;
                v15 = Long.rotateRight(v15 ^ v0, 32);
                v10 += v15;
                v5 = Long.rotateRight(v5 ^ v10, 24);
                v0 = v0 + v5 + s9;
                v15 = Long.rotateRight(v15 ^ v0, 16);
                v10 += v15;
                v5 = Long.rotateRight(v5 ^ v10, 63);

                v1 = v1 + v6 + s10;
                v12 = Long.rotateRight(v12 ^ v1, 32);
                v11 += v12;
                v6 = Long.rotateRight(v6 ^ v11, 24);
                v1 = v1 + v6 + s11;
                v12 = Long.rotateRight(v12 ^ v1, 16);
                v11 += v12;
                v6 = Long.rotateRight(v6 ^ v11, 63);

                v2 = v2 + v7 + s12;
                v13 = Long.rotateRight(v13 ^ v2, 32);
                v8 += v13;
                v7 = Long.rotateRight(v7 ^ v8, 24);
                v2 = v2 + v7 + s13;
                v13 = Long.rotateRight(v13 ^ v2, 16);
                v8 += v13;
                v7 = Long.rotateRight(v7 ^ v8, 63);

                v3 = v3 + v4 + s14;
                v14 = Long.rotateRight(v14 ^ v3, 32);
                v9 += v14;
                v4 = Long.rotateRight(v4 ^ v9, 24);
                v3 = v3 + v4 + s15;
                v14 = Long.rotateRight(v14 ^ v3, 16);
                v9 += v14;
                v4 = Long.rotateRight(v4 ^ v9, 63);
            }

            h0 ^= v0 ^ v8;
            h1 ^= v1 ^ v9;
            h2 ^= v2 ^ v10;
            h3 ^= v3 ^ v11;
            h4 ^= v4 ^ v12;
            h5 ^= v5 ^ v13;
            h6 ^= v6 ^ v14;
            h7 ^= v7 ^ v15;

            if (last) {
                break;
            }
        }

        if (output != null) {
            writeLongLittleEndian(output, outputOffset, h0);
            writeLongLittleEndian(output, outputOffset + 8, h1);
            writeLongLittleEndian(output, outputOffset + 16, h2);
            writeLongLittleEndian(output, outputOffset + 24, h3);
            return null;
        }
        if ((h0 | h1) != 0L) {
            return factory.create(h0, h1);
        }
        if ((h2 | h3) != 0L) {
            return factory.create(h2, h3);
        }
        return null;
    }

    public static final class PreparedKey {
        private final long h0;
        private final long h1;
        private final long h2;
        private final long h3;
        private final long h4;
        private final long h5;
        private final long h6;
        private final long h7;
        private final long emptyH0;
        private final long emptyH1;
        private final long emptyH2;
        private final long emptyH3;

        private PreparedKey(final byte[] key) {
            Objects.requireNonNull(key, "key");
            if (key.length != KEY_BYTES) {
                throw new IllegalArgumentException("BLAKE2b-256 key must be exactly " + KEY_BYTES + " bytes");
            }

            long[] message = new long[16];
            message[0] = readLongLittleEndian(key, 0);
            message[1] = readLongLittleEndian(key, 8);
            message[2] = readLongLittleEndian(key, 16);
            message[3] = readLongLittleEndian(key, 24);
            long[] chain = new long[8];
            long[] state = new long[16];

            initializeChain(chain);
            compress(chain, message, state, BLOCK_BYTES, 0L, false);
            this.h0 = chain[0];
            this.h1 = chain[1];
            this.h2 = chain[2];
            this.h3 = chain[3];
            this.h4 = chain[4];
            this.h5 = chain[5];
            this.h6 = chain[6];
            this.h7 = chain[7];

            initializeChain(chain);
            compress(chain, message, state, BLOCK_BYTES, 0L, true);
            this.emptyH0 = chain[0];
            this.emptyH1 = chain[1];
            this.emptyH2 = chain[2];
            this.emptyH3 = chain[3];

        }

        public byte[] hash(final byte[] input) {
            Objects.requireNonNull(input, "input");
            byte[] output = new byte[OUTPUT_BYTES];
            Blake2b.hash(null, this, input, 0, input.length, output, 0);
            return output;
        }

        public void hash(final byte[] input, final byte[] output) {
            Objects.requireNonNull(input, "input");
            Blake2b.hash(null, this, input, 0, input.length, output, 0);
        }

        public void hash(final byte[] input, final int inputOffset, final int inputLength, final byte[] output, final int outputOffset) {
            Blake2b.hash(null, this, input, inputOffset, inputLength, output, outputOffset);
        }

        void hashFixed16(final long m0, final long m1, final byte[] output, final int outputOffset) {
            Objects.requireNonNull(output, "output");
            Objects.checkFromIndexSize(outputOffset, OUTPUT_BYTES, output.length);
            Blake2b.hashScalar(null, this, null, 0, FIXED_16_BYTES, m0, m1, 0L, output, outputOffset, null);
        }

        void hashFixed20(final long m0, final long m1, final long m2, final byte[] output, final int outputOffset) {
            Objects.requireNonNull(output, "output");
            Objects.checkFromIndexSize(outputOffset, OUTPUT_BYTES, output.length);
            Blake2b.hashScalar(null, this, null, 0, FIXED_20_BYTES, m0, m1, m2, output, outputOffset, null);
        }

        <R> R hashFixed16(final long m0, final long m1, final Seed128Factory<R> factory) {
            Objects.requireNonNull(factory, "factory");
            return Blake2b.hashScalar(null, this, null, 0, FIXED_16_BYTES, m0, m1, 0L, null, 0, factory);
        }

        <R> R hashFixed20(final long m0, final long m1, final long m2, final Seed128Factory<R> factory) {
            Objects.requireNonNull(factory, "factory");
            return Blake2b.hashScalar(null, this, null, 0, FIXED_20_BYTES, m0, m1, m2, null, 0, factory);
        }

        private void writeEmptyHash(final byte[] output, final int outputOffset) {
            writeLongLittleEndian(output, outputOffset, this.emptyH0);
            writeLongLittleEndian(output, outputOffset + 8, this.emptyH1);
            writeLongLittleEndian(output, outputOffset + 16, this.emptyH2);
            writeLongLittleEndian(output, outputOffset + 24, this.emptyH3);
        }
    }

    private static void initializeChain(final long[] chain) {
        chain[0] = IV_0 ^ PARAMETER_BLOCK;
        chain[1] = IV_1;
        chain[2] = IV_2;
        chain[3] = IV_3;
        chain[4] = IV_4;
        chain[5] = IV_5;
        chain[6] = IV_6;
        chain[7] = IV_7;
    }

    private static void compress(final long[] chain, final long[] message, final long[] state, final long t0, final long t1, final boolean last) {
        System.arraycopy(chain, 0, state, 0, chain.length);
        state[8] = IV_0;
        state[9] = IV_1;
        state[10] = IV_2;
        state[11] = IV_3;
        state[12] = IV_4 ^ t0;
        state[13] = IV_5 ^ t1;
        state[14] = IV_6 ^ (last ? -1L : 0L);
        state[15] = IV_7;

        for (int round = 0; round < 12; round++) {
            switch (round) {
                case 0, 10 -> round(message, state, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
                case 1, 11 -> round(message, state, 14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3);
                case 2 -> round(message, state, 11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4);
                case 3 -> round(message, state, 7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8);
                case 4 -> round(message, state, 9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13);
                case 5 -> round(message, state, 2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9);
                case 6 -> round(message, state, 12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11);
                case 7 -> round(message, state, 13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10);
                case 8 -> round(message, state, 6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5);
                case 9 -> round(message, state, 10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0);
                default -> throw new AssertionError("Invalid BLAKE2b round: " + round);
            }
        }

        for (int i = 0; i < chain.length; i++) {
            chain[i] ^= state[i] ^ state[i + 8];
        }
    }

    private static void round(final long[] message, final long[] state, final int s0, final int s1, final int s2, final int s3, final int s4, final int s5, final int s6, final int s7, final int s8, final int s9, final int s10, final int s11, final int s12, final int s13, final int s14, final int s15) {
        mix(message[s0], message[s1], 0, 4, 8, 12, state);
        mix(message[s2], message[s3], 1, 5, 9, 13, state);
        mix(message[s4], message[s5], 2, 6, 10, 14, state);
        mix(message[s6], message[s7], 3, 7, 11, 15, state);
        mix(message[s8], message[s9], 0, 5, 10, 15, state);
        mix(message[s10], message[s11], 1, 6, 11, 12, state);
        mix(message[s12], message[s13], 2, 7, 8, 13, state);
        mix(message[s14], message[s15], 3, 4, 9, 14, state);
    }

    private static void mix(final long x, final long y, final int a, final int b, final int c, final int d, final long[] state) {
        state[a] = state[a] + state[b] + x;
        state[d] = Long.rotateRight(state[d] ^ state[a], 32);
        state[c] += state[d];
        state[b] = Long.rotateRight(state[b] ^ state[c], 24);
        state[a] = state[a] + state[b] + y;
        state[d] = Long.rotateRight(state[d] ^ state[a], 16);
        state[c] += state[d];
        state[b] = Long.rotateRight(state[b] ^ state[c], 63);
    }

    private static long readBlockWord(final byte[] input, final int blockOffset, final int blockLength, final int wordOffset) {
        int available = blockLength - wordOffset;
        if (available <= 0) {
            return 0L;
        }
        if (available >= Long.BYTES) {
            return readLongLittleEndian(input, blockOffset + wordOffset);
        }
        int length = Math.min(available, Long.BYTES);
        long value = 0L;
        for (int i = 0; i < length; i++) {
            value |= (input[blockOffset + wordOffset + i] & 0xFFL) << i * Byte.SIZE;
        }
        return value;
    }

    private static long readLongLittleEndian(final byte[] input, final int offset) {
        return (input[offset] & 0xFFL)
            | (input[offset + 1] & 0xFFL) << 8
            | (input[offset + 2] & 0xFFL) << 16
            | (input[offset + 3] & 0xFFL) << 24
            | (input[offset + 4] & 0xFFL) << 32
            | (input[offset + 5] & 0xFFL) << 40
            | (input[offset + 6] & 0xFFL) << 48
            | (input[offset + 7] & 0xFFL) << 56;
    }

    private static void writeLongLittleEndian(final byte[] output, final int offset, final long value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
        output[offset + 4] = (byte) (value >>> 32);
        output[offset + 5] = (byte) (value >>> 40);
        output[offset + 6] = (byte) (value >>> 48);
        output[offset + 7] = (byte) (value >>> 56);
    }
}
