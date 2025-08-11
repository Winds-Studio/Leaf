package su.plo.matter;

import java.util.Arrays;

public class HashingBlake3 {

    // https://en.wikipedia.org/wiki/BLAKE_(hash_function)
    // https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/digests/Blake3Digest.java

    // BLAKE3 constants
    private static final int NUMWORDS = 8;
    private static final int BLOCKLEN = NUMWORDS * 4 * 2; // 64 bytes

    // Flags
    private static final int CHUNKSTART = 1;
    private static final int CHUNKEND = 2;
    private static final int ROOT = 8;
    private static final int KEYEDHASH = 16;

    // State positions
    private static final int COUNT0 = 12, COUNT1 = 13, DATALEN = 14, FLAGS = 15;

    // BLAKE3 IV
    private static final int[] IV = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };

    public static long[] hashWorldSeed(long[] worldSeed) {
        byte[] input = longsToBytes(worldSeed);

        Blake3SinglePass hasher = new Blake3SinglePass();
        int[] hash32 = hasher.hashToWords(input, 64);

        return wordsToLongs(hash32);
    }

    public static void hash(long[] message, long[] chainValue, long[] internalState, long messageOffset, boolean isFinal) {
        assert message.length == 16;
        assert chainValue.length == 8;
        assert internalState.length == 16;

        byte[] messageBytes = longsToBytes(message);
        byte[] keyBytes = longsToBytes(chainValue);

        Blake3SinglePass hasher = new Blake3SinglePass();
        hasher.initWithKey(keyBytes);

        hasher.setCounter(messageOffset);

        hasher.update(messageBytes);

        int[] result32 = hasher.finalizeToWords(64);
        long[] result64 = wordsToLongs(result32);

        for (int i = 0; i < 8; i++) {
            chainValue[i] ^= result64[i];
        }
    }

    private static class Blake3SinglePass {
        private final int[] key = new int[NUMWORDS];
        private final int[] chaining = new int[NUMWORDS];
        private final int[] state = new int[NUMWORDS * 2];
        private final int[] message = new int[NUMWORDS * 2];
        private final byte[] indices = new byte[NUMWORDS * 2];

        private int mode = 0;
        private long counter = 0;
        private int currentBytes = 0;
        private boolean finalized = false;

        public Blake3SinglePass() {
            initNullKey();
            reset();
        }

        public void initWithKey(byte[] keyBytes) {
            if (keyBytes.length >= 32) {
                littleEndianToInt(keyBytes, 0, key);
                mode = KEYEDHASH;
            } else {
                initNullKey();
            }
            reset();
        }

        public void setCounter(long counter) {
            this.counter = counter;
        }

        private void initNullKey() {
            System.arraycopy(IV, 0, key, 0, NUMWORDS);
            mode = 0;
        }

        private void reset() {
            currentBytes = 0;
            finalized = false;
            System.arraycopy(key, 0, chaining, 0, NUMWORDS);
        }

        public void update(byte[] data) {
            if (finalized) {
                throw new IllegalStateException("Already finalized");
            }

            if (data.length <= BLOCKLEN) {
                processSingleBlock(data);
            } else {
                for (int offset = 0; offset < data.length; offset += BLOCKLEN) {
                    int blockSize = Math.min(BLOCKLEN, data.length - offset);
                    byte[] block = new byte[BLOCKLEN];
                    System.arraycopy(data, offset, block, 0, blockSize);
                    processSingleBlock(block);
                }
            }
        }

        private void processSingleBlock(byte[] block) {
            initChunkBlock(block.length, true);
            initMessage(block, 0);
            compress();
            currentBytes += block.length;
        }

        public int[] finalizeToWords(int outputBytes) {
            if (!finalized) {
                finalized = true;
            }

            int outputWords = (outputBytes + 3) / 4;
            int[] result = new int[outputWords];

            generateOutput(result, outputBytes);
            return result;
        }

        public int[] hashToWords(byte[] data, int outputBytes) {
            reset();
            update(data);
            return finalizeToWords(outputBytes);
        }

        private void generateOutput(int[] output, int outputBytes) {
            int wordsNeeded = (outputBytes + 3) / 4;
            int outputCounter = 0;
            int outputPos = 0;

            while (outputPos < wordsNeeded) {
                System.arraycopy(chaining, 0, state, 0, NUMWORDS);
                System.arraycopy(IV, 0, state, NUMWORDS, 4);

                state[COUNT0] = (int) outputCounter;
                state[COUNT1] = (int) (outputCounter >>> 32);
                state[DATALEN] = outputBytes;
                state[FLAGS] = mode | ROOT;

                Arrays.fill(message, 0);

                compress();

                int toCopy = Math.min(NUMWORDS, wordsNeeded - outputPos);
                for (int i = 0; i < toCopy; i++) {
                    output[outputPos + i] = state[i] ^ state[i + NUMWORDS];
                }

                outputPos += toCopy;
                outputCounter++;
            }
        }

        private void compress() {
            final int[] st = state;
            final int[] msg = message;
            final int[] ch = chaining;

            mixG(msg[0], msg[1], 0, 4, 8, 12, st);
            mixG(msg[2], msg[3], 1, 5, 9, 13, st);
            mixG(msg[4], msg[5], 2, 6, 10, 14, st);
            mixG(msg[6], msg[7], 3, 7, 11, 15, st);
            mixG(msg[8], msg[9], 0, 5, 10, 15, st);
            mixG(msg[10], msg[11], 1, 6, 11, 12, st);
            mixG(msg[12], msg[13], 2, 7, 8, 13, st);
            mixG(msg[14], msg[15], 3, 4, 9, 14, st);

            mixG(msg[2], msg[6], 0, 4, 8, 12, st);
            mixG(msg[3], msg[10], 1, 5, 9, 13, st);
            mixG(msg[7], msg[0], 2, 6, 10, 14, st);
            mixG(msg[4], msg[13], 3, 7, 11, 15, st);
            mixG(msg[1], msg[11], 0, 5, 10, 15, st);
            mixG(msg[12], msg[5], 1, 6, 11, 12, st);
            mixG(msg[9], msg[14], 2, 7, 8, 13, st);
            mixG(msg[15], msg[8], 3, 4, 9, 14, st);

            mixG(msg[3], msg[4], 0, 4, 8, 12, st);
            mixG(msg[10], msg[12], 1, 5, 9, 13, st);
            mixG(msg[13], msg[2], 2, 6, 10, 14, st);
            mixG(msg[7], msg[5], 3, 7, 11, 15, st);
            mixG(msg[6], msg[14], 0, 5, 10, 15, st);
            mixG(msg[0], msg[1], 1, 6, 11, 12, st);
            mixG(msg[15], msg[11], 2, 7, 8, 13, st);
            mixG(msg[8], msg[9], 3, 4, 9, 14, st);

            mixG(msg[10], msg[7], 0, 4, 8, 12, st);
            mixG(msg[12], msg[0], 1, 5, 9, 13, st);
            mixG(msg[5], msg[3], 2, 6, 10, 14, st);
            mixG(msg[13], msg[1], 3, 7, 11, 15, st);
            mixG(msg[4], msg[11], 0, 5, 10, 15, st);
            mixG(msg[2], msg[6], 1, 6, 11, 12, st);
            mixG(msg[8], msg[14], 2, 7, 8, 13, st);
            mixG(msg[9], msg[15], 3, 4, 9, 14, st);

            mixG(msg[12], msg[13], 0, 4, 8, 12, st);
            mixG(msg[0], msg[2], 1, 5, 9, 13, st);
            mixG(msg[1], msg[10], 2, 6, 10, 14, st);
            mixG(msg[5], msg[6], 3, 7, 11, 15, st);
            mixG(msg[7], msg[14], 0, 5, 10, 15, st);
            mixG(msg[3], msg[4], 1, 6, 11, 12, st);
            mixG(msg[9], msg[11], 2, 7, 8, 13, st);
            mixG(msg[15], msg[8], 3, 4, 9, 14, st);

            mixG(msg[0], msg[5], 0, 4, 8, 12, st);
            mixG(msg[2], msg[3], 1, 5, 9, 13, st);
            mixG(msg[6], msg[12], 2, 6, 10, 14, st);
            mixG(msg[1], msg[4], 3, 7, 11, 15, st);
            mixG(msg[13], msg[11], 0, 5, 10, 15, st);
            mixG(msg[10], msg[7], 1, 6, 11, 12, st);
            mixG(msg[15], msg[14], 2, 7, 8, 13, st);
            mixG(msg[8], msg[9], 3, 4, 9, 14, st);

            mixG(msg[2], msg[1], 0, 4, 8, 12, st);
            mixG(msg[3], msg[10], 1, 5, 9, 13, st);
            mixG(msg[4], msg[0], 2, 6, 10, 14, st);
            mixG(msg[6], msg[7], 3, 7, 11, 15, st);
            mixG(msg[5], msg[14], 0, 5, 10, 15, st);
            mixG(msg[12], msg[13], 1, 6, 11, 12, st);
            mixG(msg[8], msg[11], 2, 7, 8, 13, st);
            mixG(msg[9], msg[15], 3, 4, 9, 14, st);

            ch[0] = st[0] ^ st[8];  ch[1] = st[1] ^ st[9];
            ch[2] = st[2] ^ st[10]; ch[3] = st[3] ^ st[11];
            ch[4] = st[4] ^ st[12]; ch[5] = st[5] ^ st[13];
            ch[6] = st[6] ^ st[14]; ch[7] = st[7] ^ st[15];
        }

        private static void mixG(int m1, int m2, int posA, int posB, int posC, int posD, int[] state) {
            state[posA] += state[posB] + m1;
            state[posD] = Integer.rotateRight(state[posD] ^ state[posA], 16);
            state[posC] += state[posD];
            state[posB] = Integer.rotateRight(state[posB] ^ state[posC], 12);
            state[posA] += state[posB] + m2;
            state[posD] = Integer.rotateRight(state[posD] ^ state[posA], 8);
            state[posC] += state[posD];
            state[posB] = Integer.rotateRight(state[posB] ^ state[posC], 7);
        }

        private void initMessage(byte[] data, int offset) {
            byte[] paddedData = new byte[BLOCKLEN];
            int copyLen = Math.min(data.length - offset, BLOCKLEN);
            System.arraycopy(data, offset, paddedData, 0, copyLen);

            littleEndianToInt(paddedData, 0, message);
        }

        private void initChunkBlock(int dataLen, boolean isFinal) {
            System.arraycopy(currentBytes == 0 ? key : chaining, 0, state, 0, NUMWORDS);
            System.arraycopy(IV, 0, state, NUMWORDS, 4);

            state[COUNT0] = (int) counter;
            state[COUNT1] = (int) (counter >>> 32);
            state[DATALEN] = dataLen;
            state[FLAGS] = mode | CHUNKSTART | (isFinal ? CHUNKEND : 0);

            if (isFinal) {
                state[FLAGS] |= ROOT;
            }
        }
    }

    private static void littleEndianToInt(byte[] data, int offset, int[] output) {
        for (int i = 0; i < output.length && (offset + i * 4 + 3) < data.length; i++) {
            output[i] = (data[offset + i * 4] & 0xFF) |
                ((data[offset + i * 4 + 1] & 0xFF) << 8) |
                ((data[offset + i * 4 + 2] & 0xFF) << 16) |
                ((data[offset + i * 4 + 3] & 0xFF) << 24);
        }
    }

    private static byte[] longsToBytes(long[] longs) {
        byte[] bytes = new byte[longs.length * 8];
        for (int i = 0; i < longs.length; i++) {
            long value = longs[i];
            for (int j = 0; j < 8; j++) {
                bytes[i * 8 + j] = (byte) (value >>> (j * 8));
            }
        }
        return bytes;
    }

    private static long[] wordsToLongs(int[] words) {
        long[] longs = new long[(words.length + 1) / 2];
        for (int i = 0; i < longs.length; i++) {
            long low = words[i * 2] & 0xFFFFFFFFL;
            long high = (i * 2 + 1 < words.length) ?
                (words[i * 2 + 1] & 0xFFFFFFFFL) << 32 : 0L;
            longs[i] = low | high;
        }
        return longs;
    }
}
