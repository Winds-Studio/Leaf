package su.plo.matter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    static final byte FAST_FACTORY = 10;
    static final byte FAST_FACTORY_RETRY = 11;
    private static final byte PROTOCOL_VERSION = 1;
    private static final byte[] PROTOCOL_NAME = "LeafSecureWorldgen".getBytes(StandardCharsets.US_ASCII);

    private TerrainHashing() {
    }

    static Mac createMac(final byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    static byte[] derive(final byte[] key, final byte tag, final byte[] context) {
        return derive(createMac(key), tag, context);
    }

    static byte[] derive(final Mac mac, final byte tag, final byte[] context) {
        mac.update(PROTOCOL_NAME);
        mac.update((byte) 0);
        mac.update(PROTOCOL_VERSION);
        mac.update(tag);
        updateInt(mac, context.length);
        mac.update(context);
        return mac.doFinal();
    }

    static void deriveBlock(final Mac mac, final long counter, final byte[] output) {
        if (output.length < TerrainCrypto.MASTER_SEED_BYTES) {
            throw new IllegalArgumentException("HMAC output buffer must contain at least " + TerrainCrypto.MASTER_SEED_BYTES + " bytes");
        }

        mac.update(PROTOCOL_NAME);
        mac.update((byte) 0);
        mac.update(PROTOCOL_VERSION);
        mac.update(BLOCK);
        updateInt(mac, Long.BYTES);
        updateLong(mac, counter);
        try {
            mac.doFinal(output, 0);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to write HmacSHA256 output", ex);
        }
    }

    private static void updateInt(final Mac mac, final int value) {
        mac.update((byte) (value >>> 24));
        mac.update((byte) (value >>> 16));
        mac.update((byte) (value >>> 8));
        mac.update((byte) value);
    }

    private static void updateLong(final Mac mac, final long value) {
        mac.update((byte) (value >>> 56));
        mac.update((byte) (value >>> 48));
        mac.update((byte) (value >>> 40));
        mac.update((byte) (value >>> 32));
        mac.update((byte) (value >>> 24));
        mac.update((byte) (value >>> 16));
        mac.update((byte) (value >>> 8));
        mac.update((byte) value);
    }

    static byte[] stringContext(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] longContext(final long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
    }

    static byte[] intContext(final int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }

    static byte[] positionContext(final int x, final int y, final int z) {
        return ByteBuffer.allocate(Integer.BYTES * 3)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(x)
            .putInt(y)
            .putInt(z)
            .array();
    }

    static long readLong(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong();
    }

}
