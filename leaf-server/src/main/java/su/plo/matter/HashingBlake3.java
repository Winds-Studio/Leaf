package su.plo.matter;

public class HashingBlake3 {
    // Simplified BLAKE3-inspired 64-bit hash for worldgen performance
    // https://en.wikipedia.org/wiki/BLAKE_(hash_function)
    // https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/digests/Blake3Digest.java

    private static final long[] IV = {
        0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL,
        0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
        0x510e527fade682d1L, 0x9b05688c2b3e6c1fL,
        0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    };

    private static final long CHUNK_START = 1L;
    private static final long CHUNK_END = 2L;
    private static final long ROOT = 8L;

    private static final int[][] SIGMA = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8},
        {3, 4, 10, 12, 13, 2, 7, 14, 6, 5, 9, 0, 11, 15, 8, 1},
        {10, 7, 12, 9, 14, 3, 13, 15, 4, 0, 11, 2, 5, 8, 1, 6}
    };

    private static final ThreadLocal<long[]> STATE_POOL = ThreadLocal.withInitial(() -> new long[16]);
    private static final ThreadLocal<long[]> PADDED_INPUT_POOL = ThreadLocal.withInitial(() -> new long[16]);

    public static long[] hashWorldSeed(long[] worldSeed) {
        long[] state = STATE_POOL.get();
        long[] result = new long[worldSeed.length];

        System.arraycopy(IV, 0, state, 0, 8);
        System.arraycopy(IV, 0, state, 8, 4);
        state[12] = 0L;
        state[13] = 0L;
        state[14] = worldSeed.length * 8;
        state[15] = CHUNK_START | CHUNK_END | ROOT;

        long[] padded = PADDED_INPUT_POOL.get();
        System.arraycopy(worldSeed, 0, padded, 0, Math.min(worldSeed.length, 16));

        fastCompress(padded, state);
        System.arraycopy(state, 0, result, 0, Math.min(result.length, 16));

        return result;
    }

    public static void hash(long[] message, long[] chainValue, long[] internalState, long messageOffset, boolean isFinal) {
        long[] state = STATE_POOL. get();

        System.arraycopy(chainValue, 0, state, 0, 8);
        System.arraycopy(IV, 0, state, 8, 4);
        state[12] = messageOffset;
        state[13] = 0L;
        state[14] = 128L;
        state[15] = CHUNK_START | CHUNK_END | ROOT;

        fastCompress(message, state);

        for (int i = 0; i < 8; i++) {
            chainValue[i] ^= state[i] ^ state[i + 8];
        }
    }

    private static void fastCompress(long[] msg, long[] state) {
        round(state, msg, SIGMA[0]);
        round(state, msg, SIGMA[1]);
        round(state, msg, SIGMA[2]);
        round(state, msg, SIGMA[3]);
    }

    private static void round(long[] state, long[] msg, int[] sigma) {
        g(state, 0, 4, 8, 12, msg[sigma[0]], msg[sigma[1]]);
        g(state, 1, 5, 9, 13, msg[sigma[2]], msg[sigma[3]]);
        g(state, 2, 6, 10, 14, msg[sigma[4]], msg[sigma[5]]);
        g(state, 3, 7, 11, 15, msg[sigma[6]], msg[sigma[7]]);
        g(state, 0, 5, 10, 15, msg[sigma[8]], msg[sigma[9]]);
        g(state, 1, 6, 11, 12, msg[sigma[10]], msg[sigma[11]]);
        g(state, 2, 7, 8, 13, msg[sigma[12]], msg[sigma[13]]);
        g(state, 3, 4, 9, 14, msg[sigma[14]], msg[sigma[15]]);
    }

    private static void g(long[] s, int a, int b, int c, int d, long mx, long my) {
        long sa = s[a] + s[b] + mx;
        long sd = Long.rotateRight(s[d] ^ sa, 32);
        long sc = s[c] + sd;
        long sb = Long.rotateRight(s[b] ^ sc, 24);

        sa += sb + my;
        sd = Long.rotateRight(sd ^ sa, 16);
        sc += sd;
        sb = Long.rotateRight(sb ^ sc, 63);

        s[a] = sa;
        s[b] = sb;
        s[c] = sc;
        s[d] = sd;
    }
}
