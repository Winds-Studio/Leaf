package su.plo.matter;

public class HashingBlake3 {

    // https://en.wikipedia.org/wiki/BLAKE_(hash_function)
    // https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/digests/Blake3Digest.java

    // BLAKE3 constants
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
        int[] state = new int[16];
        int[] result = new int[16];

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
        // Round 0
        g(state, 0, 4, 8, 12, msg[0], msg[1]);
        g(state, 1, 5, 9, 13, msg[2], msg[3]);
        g(state, 2, 6, 10, 14, msg[4], msg[5]);
        g(state, 3, 7, 11, 15, msg[6], msg[7]);
        g(state, 0, 5, 10, 15, msg[8], msg[9]);
        g(state, 1, 6, 11, 12, msg[10], msg[11]);
        g(state, 2, 7, 8, 13, msg[12], msg[13]);
        g(state, 3, 4, 9, 14, msg[14], msg[15]);

        // Round 1
        int[] s1 = SIGMA[1];
        g(state, 0, 4, 8, 12, msg[s1[0]], msg[s1[1]]);
        g(state, 1, 5, 9, 13, msg[s1[2]], msg[s1[3]]);
        g(state, 2, 6, 10, 14, msg[s1[4]], msg[s1[5]]);
        g(state, 3, 7, 11, 15, msg[s1[6]], msg[s1[7]]);
        g(state, 0, 5, 10, 15, msg[s1[8]], msg[s1[9]]);
        g(state, 1, 6, 11, 12, msg[s1[10]], msg[s1[11]]);
        g(state, 2, 7, 8, 13, msg[s1[12]], msg[s1[13]]);
        g(state, 3, 4, 9, 14, msg[s1[14]], msg[s1[15]]);

        // Round 2
        int[] s2 = SIGMA[2];
        g(state, 0, 4, 8, 12, msg[s2[0]], msg[s2[1]]);
        g(state, 1, 5, 9, 13, msg[s2[2]], msg[s2[3]]);
        g(state, 2, 6, 10, 14, msg[s2[4]], msg[s2[5]]);
        g(state, 3, 7, 11, 15, msg[s2[6]], msg[s2[7]]);
        g(state, 0, 5, 10, 15, msg[s2[8]], msg[s2[9]]);
        g(state, 1, 6, 11, 12, msg[s2[10]], msg[s2[11]]);
        g(state, 2, 7, 8, 13, msg[s2[12]], msg[s2[13]]);
        g(state, 3, 4, 9, 14, msg[s2[14]], msg[s2[15]]);

        // Round 3
        int[] s3 = SIGMA[3];
        g(state, 0, 4, 8, 12, msg[s3[0]], msg[s3[1]]);
        g(state, 1, 5, 9, 13, msg[s3[2]], msg[s3[3]]);
        g(state, 2, 6, 10, 14, msg[s3[4]], msg[s3[5]]);
        g(state, 3, 7, 11, 15, msg[s3[6]], msg[s3[7]]);
        g(state, 0, 5, 10, 15, msg[s3[8]], msg[s3[9]]);
        g(state, 1, 6, 11, 12, msg[s3[10]], msg[s3[11]]);
        g(state, 2, 7, 8, 13, msg[s3[12]], msg[s3[13]]);
        g(state, 3, 4, 9, 14, msg[s3[14]], msg[s3[15]]);

        // Round 4
        int[] s4 = SIGMA[4];
        g(state, 0, 4, 8, 12, msg[s4[0]], msg[s4[1]]);
        g(state, 1, 5, 9, 13, msg[s4[2]], msg[s4[3]]);
        g(state, 2, 6, 10, 14, msg[s4[4]], msg[s4[5]]);
        g(state, 3, 7, 11, 15, msg[s4[6]], msg[s4[7]]);
        g(state, 0, 5, 10, 15, msg[s4[8]], msg[s4[9]]);
        g(state, 1, 6, 11, 12, msg[s4[10]], msg[s4[11]]);
        g(state, 2, 7, 8, 13, msg[s4[12]], msg[s4[13]]);
        g(state, 3, 4, 9, 14, msg[s4[14]], msg[s4[15]]);

        // Round 5
        int[] s5 = SIGMA[5];
        g(state, 0, 4, 8, 12, msg[s5[0]], msg[s5[1]]);
        g(state, 1, 5, 9, 13, msg[s5[2]], msg[s5[3]]);
        g(state, 2, 6, 10, 14, msg[s5[4]], msg[s5[5]]);
        g(state, 3, 7, 11, 15, msg[s5[6]], msg[s5[7]]);
        g(state, 0, 5, 10, 15, msg[s5[8]], msg[s5[9]]);
        g(state, 1, 6, 11, 12, msg[s5[10]], msg[s5[11]]);
        g(state, 2, 7, 8, 13, msg[s5[12]], msg[s5[13]]);
        g(state, 3, 4, 9, 14, msg[s5[14]], msg[s5[15]]);

        // Round 6
        int[] s6 = SIGMA[6];
        g(state, 0, 4, 8, 12, msg[s6[0]], msg[s6[1]]);
        g(state, 1, 5, 9, 13, msg[s6[2]], msg[s6[3]]);
        g(state, 2, 6, 10, 14, msg[s6[4]], msg[s6[5]]);
        g(state, 3, 7, 11, 15, msg[s6[6]], msg[s6[7]]);
        g(state, 0, 5, 10, 15, msg[s6[8]], msg[s6[9]]);
        g(state, 1, 6, 11, 12, msg[s6[10]], msg[s6[11]]);
        g(state, 2, 7, 8, 13, msg[s6[12]], msg[s6[13]]);
        g(state, 3, 4, 9, 14, msg[s6[14]], msg[s6[15]]);
    }

    private static void g(int[] state, int a, int b, int c, int d, int mx, int my) {
        state[a] += state[b] + mx;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 16);
        state[c] += state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 12);
        state[a] += state[b] + my;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 8);
        state[c] += state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 7);
    }

    private static int[] longsToInts(long[] longs) {
        int[] ints = new int[longs.length * 2];
        for (int i = 0; i < longs.length; i++) {
            ints[i * 2] = (int) longs[i];
            ints[i * 2 + 1] = (int) (longs[i] >>> 32);
        }
        return ints;
    }

    private static long[] intsToLongs(int[] ints) {
        long[] longs = new long[(ints.length + 1) / 2];
        for (int i = 0; i < longs.length; i++) {
            long low = ints[i * 2] & 0xFFFFFFFFL;
            long high = (i * 2 + 1 < ints.length) ?
                ((long) ints[i * 2 + 1] << 32) : 0L;
            longs[i] = low | high;
        }
        return longs;
    }
}
