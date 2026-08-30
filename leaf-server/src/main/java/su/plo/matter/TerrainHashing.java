package su.plo.matter;

import java.nio.charset.StandardCharsets;

final class TerrainHashing {
    static final byte DIMENSION = 1;
    static final byte DOMAIN = 2;
    static final byte BLOCK = 3;
    static final byte FORK = 4;
    static final byte POSITIONAL_FORK = 5;
    static final byte POSITIONAL_AT = 6;
    static final byte POSITIONAL_HASH = 7;
    static final byte POSITIONAL_SEED = 8;
    static final byte SET_SEED = 9;
    static final byte RUNTIME_AT = 10;
    static final byte RUNTIME_HASH = 11;
    static final byte RUNTIME_SEED = 12;
    static final byte RUNTIME_RETRY = 13;
    private static final byte PROTOCOL_VERSION = 1;
    private static final byte[] PROTOCOL_NAME = "LeafSecureWorldgen".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EMPTY_CONTEXT = new byte[0];
    private static final int GENERAL_HEADER_BYTES = PROTOCOL_NAME.length + 1 + 1 + 1 + Integer.BYTES;

    private TerrainHashing() {
    }

    static Blake2b.PreparedKey prepare(final byte[] key) {
        return Blake2b.prepare(key);
    }

    static Blake2b.PreparedKey derivePrepared(final byte[] key, final byte tag, final byte[] context) {
        return derivePrepared(prepare(key), tag, context);
    }

    static Blake2b.PreparedKey derivePrepared(final Blake2b.PreparedKey key, final byte tag, final byte[] context) {
        byte[] derivedKey = derive(key, tag, context);
        return prepare(derivedKey);
    }

    static Blake2b.PreparedKey derivePreparedLong(final Blake2b.PreparedKey key, final byte tag, final long value) {
        byte[] derivedKey = new byte[Blake2b.OUTPUT_BYTES];
        key.hashFixed16(fixedHeader(tag), Long.reverseBytes(value), derivedKey, 0);
        return prepare(derivedKey);
    }

    static Blake2b.PreparedKey derivePreparedPosition(final Blake2b.PreparedKey key, final byte tag, final int x, final int y, final int z) {
        byte[] derivedKey = new byte[Blake2b.OUTPUT_BYTES];
        key.hashFixed16(positionHeader(tag, x), packInts(y, z), derivedKey, 0);
        return prepare(derivedKey);
    }

    static ChaCha12.PreparedKey deriveChaChaKey(final Blake2b.PreparedKey key, final byte tag) {
        return ChaCha12.prepare(derive(key, tag, EMPTY_CONTEXT));
    }

    static byte[] derive(final Blake2b.PreparedKey key, final byte tag, final byte[] context) {
        byte[] message = buildHashFrame(tag, context);
        byte[] output = new byte[Blake2b.OUTPUT_BYTES];
        key.hash(message, output);
        return output;
    }

    static void deriveBlock(final Blake2b.PreparedKey key, final long counter, final byte[] output) {
        key.hashFixed16(fixedHeader(BLOCK), Long.reverseBytes(counter), output, 0);
    }

    static <R> R deriveRuntimePosition(final ChaCha12.PreparedKey key, final int x, final int y, final int z, final ChaCha12.Seed128Factory<R> factory) {
        return key.derive(x, y, z, factory);
    }

    static <R> R deriveRuntimeSeed(final ChaCha12.PreparedKey key, final long seed, final ChaCha12.Seed128Factory<R> factory) {
        return key.derive((int) seed, (int) (seed >>> Integer.SIZE), 0, factory);
    }

    static <R> R deriveRuntimeHash(final Blake2b.PreparedKey key, final String value, final Blake2b.Seed128Factory<R> factory) {
        byte[] context = stringToBytes(value);
        byte[] digest = derive(key, RUNTIME_HASH, context);
        R result = createFromDigest(digest, factory);
        if (result != null) {
            return result;
        }

        int attempt = 0;
        while (true) {
            if (attempt == Integer.MAX_VALUE) {
                throw new IllegalStateException("Unable to derive a non-zero secure terrain runtime hash seed");
            }
            attempt++;
            byte[] retryContext = new byte[1 + Integer.BYTES + context.length];
            retryContext[0] = RUNTIME_HASH;
            writeInt(retryContext, 1, attempt);
            System.arraycopy(context, 0, retryContext, 1 + Integer.BYTES, context.length);
            digest = derive(key, RUNTIME_RETRY, retryContext);
            result = createFromDigest(digest, factory);
            if (result != null) {
                return result;
            }
        }
    }

    static byte[] stringToBytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildHashFrame(final byte tag, final byte[] context) {
        byte[] message = new byte[Math.addExact(GENERAL_HEADER_BYTES, context.length)];
        System.arraycopy(PROTOCOL_NAME, 0, message, 0, PROTOCOL_NAME.length);
        int offset = PROTOCOL_NAME.length;
        message[offset++] = 0;
        message[offset++] = PROTOCOL_VERSION;
        message[offset++] = tag;
        writeInt(message, offset, context.length);
        System.arraycopy(context, 0, message, offset + Integer.BYTES, context.length);
        return message;
    }

    private static long fixedHeader(final byte tag) {
        return Byte.toUnsignedLong(PROTOCOL_VERSION) | Byte.toUnsignedLong(tag) << Byte.SIZE;
    }

    private static long positionHeader(final byte tag, final int x) {
        return fixedHeader(tag) | Integer.toUnsignedLong(Integer.reverseBytes(x)) << Integer.SIZE;
    }

    private static long packInts(final int first, final int second) {
        return Integer.toUnsignedLong(Integer.reverseBytes(first))
            | Integer.toUnsignedLong(Integer.reverseBytes(second)) << Integer.SIZE;
    }

    private static <R> R createFromDigest(final byte[] digest, final Blake2b.Seed128Factory<R> factory) {
        long seedLo = readLongLittleEndian(digest, 0);
        long seedHi = readLongLittleEndian(digest, Long.BYTES);
        if ((seedLo | seedHi) != 0L) {
            return factory.create(seedLo, seedHi);
        }

        seedLo = readLongLittleEndian(digest, Long.BYTES * 2);
        seedHi = readLongLittleEndian(digest, Long.BYTES * 3);
        return (seedLo | seedHi) == 0L ? null : factory.create(seedLo, seedHi);
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

    private static void writeInt(final byte[] output, final int offset, final int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }
}
