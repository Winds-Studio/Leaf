package su.plo.matter;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

public class HashingBlake3 {

    // https://en.wikipedia.org/wiki/BLAKE_(hash_function)
    // https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/digests/Blake3Digest.java

    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    static {
        if (SPECIES.length() < 4) {
            throw new UnsupportedOperationException("SIMD with at least 4 lanes is required");
        }
    }

    private static final int[] IV = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };

    // Flags
    private static final int CHUNK_START = 1;
    private static final int CHUNK_END = 2;
    private static final int ROOT = 8;

    private static final int[][] SIGMA = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8},
        {3, 4, 10, 12, 13, 2, 7, 14, 6, 5, 9, 0, 11, 15, 8, 1},
        {10, 7, 12, 9, 14, 3, 13, 15, 4, 0, 11, 2, 5, 8, 1, 6},
        {12, 13, 9, 11, 15, 10, 14, 8, 7, 2, 5, 3, 0, 1, 6, 4},
        {9, 14, 11, 5, 8, 12, 15, 1, 13, 3, 0, 10, 2, 6, 4, 7},
        {11, 15, 5, 0, 1, 9, 8, 6, 14, 10, 2, 12, 3, 4, 7, 13}
    };

    private static final ThreadLocal<int[][]> STATE_POOL = ThreadLocal.withInitial(() -> {
        int[][] pool = new int[4][];
        for (int i = 0; i < 4; i++) {
            pool[i] = new int[16];
        }
        return pool;
    });

    private static final ThreadLocal<int[]> RESULT_POOL = ThreadLocal.withInitial(() -> new int[16]);
    private static final ThreadLocal<Integer> POOL_INDEX = ThreadLocal.withInitial(() -> 0);

    public static long[] hashWorldSeed(long[] worldSeed) {
        int[] input32 = longsToInts(worldSeed);
        int[] result32 = fastHash(input32, input32.length * 4);
        return intsToLongs(result32);
    }

    public static void hash(long[] message, long[] chainValue, long[] internalState, long messageOffset, boolean isFinal) {
        int[] msg32 = longsToInts(message);
        int[] cv32 = longsToInts(chainValue);

        int[] state = new int[16];
        System.arraycopy(cv32, 0, state, 0, 8);
        System.arraycopy(IV, 0, state, 8, 4);

        state[12] = (int) messageOffset;
        state[13] = (int) (messageOffset >>> 32);
        state[14] = 64; // block length
        state[15] = CHUNK_START | CHUNK_END | ROOT;

        fastCompress(msg32, state);

        for (int i = 0; i < 8; i++) {
            cv32[i] ^= state[i] ^ state[i + 8];
        }

        for (int i = 0; i < 4; i++) {
            chainValue[i] = ((long) cv32[i * 2 + 1] << 32) | (cv32[i * 2] & 0xFFFFFFFFL);
        }
    }

    private static int[] fastHash(int[] input, int inputBytes) {
        int[][] statePool = STATE_POOL.get();
        int poolIdx = POOL_INDEX.get();
        int[] state = statePool[poolIdx];
        POOL_INDEX.set((poolIdx + 1) & 3);

        int[] result = RESULT_POOL.get();

        System.arraycopy(IV, 0, state, 0, 8);
        System.arraycopy(IV, 0, state, 8, 4);

        state[12] = 0;
        state[13] = 0;
        state[14] = inputBytes;
        state[15] = CHUNK_START | CHUNK_END | ROOT;

        int[] paddedInput = new int[16];
        System.arraycopy(input, 0, paddedInput, 0, Math.min(input.length, 16));

        fastCompress(paddedInput, state);

        System.arraycopy(state, 0, result, 0, 16);

        return result;
    }

    private static void fastCompress(int[] msg, int[] state) {
        final int[] sigma0 = SIGMA[0];
        final int[] sigma1 = SIGMA[1];
        final int[] sigma2 = SIGMA[2];
        final int[] sigma3 = SIGMA[3];
        final int[] sigma4 = SIGMA[4];
        final int[] sigma5 = SIGMA[5];
        final int[] sigma6 = SIGMA[6];

        final int[] msgCache = new int[16];
        for (int j = 0; j < 16; j++) {
            msgCache[j] = msg[j];
        }

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma0[0]], msgCache[sigma0[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma0[2]], msgCache[sigma0[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma0[4]], msgCache[sigma0[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma0[6]], msgCache[sigma0[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma0[8]], msgCache[sigma0[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma0[10]], msgCache[sigma0[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma0[12]], msgCache[sigma0[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma0[14]], msgCache[sigma0[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma1[0]], msgCache[sigma1[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma1[2]], msgCache[sigma1[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma1[4]], msgCache[sigma1[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma1[6]], msgCache[sigma1[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma1[8]], msgCache[sigma1[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma1[10]], msgCache[sigma1[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma1[12]], msgCache[sigma1[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma1[14]], msgCache[sigma1[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma2[0]], msgCache[sigma2[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma2[2]], msgCache[sigma2[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma2[4]], msgCache[sigma2[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma2[6]], msgCache[sigma2[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma2[8]], msgCache[sigma2[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma2[10]], msgCache[sigma2[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma2[12]], msgCache[sigma2[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma2[14]], msgCache[sigma2[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma3[0]], msgCache[sigma3[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma3[2]], msgCache[sigma3[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma3[4]], msgCache[sigma3[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma3[6]], msgCache[sigma3[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma3[8]], msgCache[sigma3[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma3[10]], msgCache[sigma3[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma3[12]], msgCache[sigma3[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma3[14]], msgCache[sigma3[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma4[0]], msgCache[sigma4[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma4[2]], msgCache[sigma4[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma4[4]], msgCache[sigma4[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma4[6]], msgCache[sigma4[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma4[8]], msgCache[sigma4[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma4[10]], msgCache[sigma4[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma4[12]], msgCache[sigma4[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma4[14]], msgCache[sigma4[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma5[0]], msgCache[sigma5[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma5[2]], msgCache[sigma5[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma5[4]], msgCache[sigma5[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma5[6]], msgCache[sigma5[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma5[8]], msgCache[sigma5[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma5[10]], msgCache[sigma5[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma5[12]], msgCache[sigma5[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma5[14]], msgCache[sigma5[15]]);

        gSIMD(state, 0, 4, 8, 12, msgCache[sigma6[0]], msgCache[sigma6[1]]);
        gSIMD(state, 1, 5, 9, 13, msgCache[sigma6[2]], msgCache[sigma6[3]]);
        gSIMD(state, 2, 6, 10, 14, msgCache[sigma6[4]], msgCache[sigma6[5]]);
        gSIMD(state, 3, 7, 11, 15, msgCache[sigma6[6]], msgCache[sigma6[7]]);
        gSIMD(state, 0, 5, 10, 15, msgCache[sigma6[8]], msgCache[sigma6[9]]);
        gSIMD(state, 1, 6, 11, 12, msgCache[sigma6[10]], msgCache[sigma6[11]]);
        gSIMD(state, 2, 7, 8, 13, msgCache[sigma6[12]], msgCache[sigma6[13]]);
        gSIMD(state, 3, 4, 9, 14, msgCache[sigma6[14]], msgCache[sigma6[15]]);
    }

    private static void gSIMD(int[] state, int a, int b, int c, int d, int mx, int my) {
        int sa = state[a];
        int sb = state[b];
        int sc = state[c];
        int sd = state[d];

        sa += sb + mx;
        sd = Integer.rotateRight(sd ^ sa, 16);
        sc += sd;
        sb = Integer.rotateRight(sb ^ sc, 12);
        sa += sb + my;
        sd = Integer.rotateRight(sd ^ sa, 8);
        sc += sd;
        sb = Integer.rotateRight(sb ^ sc, 7);

        state[a] = sa;
        state[b] = sb;
        state[c] = sc;
        state[d] = sd;
    }

    private static int[] longsToInts(long[] longs) {
        int[] ints = new int[longs.length * 2];
        int i = 0;
        int len = longs.length;

        for (; i + 3 < len; i += 4) {
            long l0 = longs[i], l1 = longs[i + 1], l2 = longs[i + 2], l3 = longs[i + 3];
            int idx = i * 2;
            ints[idx] = (int) l0;
            ints[idx + 1] = (int) (l0 >>> 32);
            ints[idx + 2] = (int) l1;
            ints[idx + 3] = (int) (l1 >>> 32);
            ints[idx + 4] = (int) l2;
            ints[idx + 5] = (int) (l2 >>> 32);
            ints[idx + 6] = (int) l3;
            ints[idx + 7] = (int) (l3 >>> 32);
        }

        for (; i < len; i++) {
            long l = longs[i];
            int idx = i * 2;
            ints[idx] = (int) l;
            ints[idx + 1] = (int) (l >>> 32);
        }
        return ints;
    }

    private static long[] intsToLongs(int[] ints) {
        int len = (ints.length + 1) / 2;
        long[] longs = new long[len];
        int i = 0;

        for (; i + 3 < len; i += 4) {
            int idx = i * 2;
            longs[i] = (ints[idx] & 0xFFFFFFFFL) | ((long) ints[idx + 1] << 32);
            longs[i + 1] = (ints[idx + 2] & 0xFFFFFFFFL) | ((long) ints[idx + 3] << 32);
            longs[i + 2] = (ints[idx + 4] & 0xFFFFFFFFL) | ((long) ints[idx + 5] << 32);
            longs[i + 3] = (ints[idx + 6] & 0xFFFFFFFFL) | ((long) ints[idx + 7] << 32);
        }

        for (; i < len; i++) {
            int idx = i * 2;
            long low = ints[idx] & 0xFFFFFFFFL;
            long high = (idx + 1 < ints.length) ? ((long) ints[idx + 1] << 32) : 0L;
            longs[i] = low | high;
        }
        return longs;
    }
}
