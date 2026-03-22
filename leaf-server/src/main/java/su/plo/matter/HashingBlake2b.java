package su.plo.matter;

public class HashingBlake2b {

    // https://en.wikipedia.org/wiki/BLAKE_(hash_function)
    // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/crypto/digests/Blake2bDigest.java

    private final static long[] blake2b_IV = {
        0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL,
        0xa54ff53a5f1d36f1L, 0x510e527fade682d1L, 0x9b05688c2b3e6c1fL,
        0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    };

    private final static byte[][] blake2b_sigma = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
        {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
        {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
        {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
        {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
        {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
        {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
        {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
        {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}
    };

    public static long[] hashWorldSeed(long[] worldSeed) {
        long[] result = blake2b_IV.clone();
        result[0] ^= 0x01010040;
        hash(worldSeed, result, new long[16], 0, false);
        return result;
    }

    public static void hash(long[] message, long[] chainValue, long[] internalState, long messageOffset, boolean isFinal) {
        assert message.length == 16;
        assert chainValue.length == 8;
        assert internalState.length == 16;

        System.arraycopy(chainValue, 0, internalState, 0, chainValue.length);
        System.arraycopy(blake2b_IV, 0, internalState, chainValue.length, 4);
        internalState[12] = messageOffset ^ blake2b_IV[4];
        internalState[13] = blake2b_IV[5];
        if (isFinal) internalState[14] = ~blake2b_IV[6];
        internalState[15] = blake2b_IV[7];

        for (int round = 0; round < 12; round++) {
            G(message[blake2b_sigma[round][0]], message[blake2b_sigma[round][1]], 0, 4, 8, 12, internalState);
            G(message[blake2b_sigma[round][2]], message[blake2b_sigma[round][3]], 1, 5, 9, 13, internalState);
            G(message[blake2b_sigma[round][4]], message[blake2b_sigma[round][5]], 2, 6, 10, 14, internalState);
            G(message[blake2b_sigma[round][6]], message[blake2b_sigma[round][7]], 3, 7, 11, 15, internalState);
            G(message[blake2b_sigma[round][8]], message[blake2b_sigma[round][9]], 0, 5, 10, 15, internalState);
            G(message[blake2b_sigma[round][10]], message[blake2b_sigma[round][11]], 1, 6, 11, 12, internalState);
            G(message[blake2b_sigma[round][12]], message[blake2b_sigma[round][13]], 2, 7, 8, 13, internalState);
            G(message[blake2b_sigma[round][14]], message[blake2b_sigma[round][15]], 3, 4, 9, 14, internalState);
        }

        for (int i = 0; i < 8; i++) {
            chainValue[i] ^= internalState[i] ^ internalState[i + 8];
        }
    }

    private static void G(long m1, long m2, int posA, int posB, int posC, int posD, long[] internalState) {
        internalState[posA] = internalState[posA] + internalState[posB] + m1;
        internalState[posD] = Long.rotateRight(internalState[posD] ^ internalState[posA], 32);
        internalState[posC] = internalState[posC] + internalState[posD];
        internalState[posB] = Long.rotateRight(internalState[posB] ^ internalState[posC], 24); // replaces 25 of BLAKE
        internalState[posA] = internalState[posA] + internalState[posB] + m2;
        internalState[posD] = Long.rotateRight(internalState[posD] ^ internalState[posA], 16);
        internalState[posC] = internalState[posC] + internalState[posD];
        internalState[posB] = Long.rotateRight(internalState[posB] ^ internalState[posC], 63); // replaces 11 of BLAKE
    }
}
