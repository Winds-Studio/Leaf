package su.plo.matter;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.dreeam.leaf.util.LeafConstants;
import org.slf4j.Logger;

public final class TerrainCrypto {
    public static final int SCHEMA_VERSION = 1;
    public static final String ALGORITHM = "hmac_sha256_v1";
    public static final int MASTER_SEED_BYTES = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Codec<Serialized> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("schema_version").forGetter(Serialized::schemaVersion),
        Codec.STRING.fieldOf("algorithm").forGetter(Serialized::algorithm),
        Codec.STRING.fieldOf("master_seed").forGetter(Serialized::masterSeed)
    ).apply(instance, Serialized::new));
    private static final Codec<TerrainCrypto> STRICT_CODEC = SERIALIZED_CODEC.comapFlatMap(TerrainCrypto::decode, settings -> new Serialized(SCHEMA_VERSION, ALGORITHM, settings.masterSeedHex()));
    public static final Codec<TerrainCrypto> CODEC = LeafConstants.DISABLE_SECURE_SEED_INTEGRITY_CHECK ? Codec.withAlternative(STRICT_CODEC, Codec.PASSTHROUGH, ignored -> recovered()) : STRICT_CODEC;

    private final byte[] masterSeed;

    private TerrainCrypto(final byte[] masterSeed) {
        if (masterSeed.length != MASTER_SEED_BYTES) {
            throw new IllegalArgumentException("Secure terrain master seed must be exactly " + MASTER_SEED_BYTES + " bytes");
        }
        this.masterSeed = masterSeed.clone();
    }

    public static TerrainCrypto random() {
        byte[] seed = new byte[MASTER_SEED_BYTES];
        SECURE_RANDOM.nextBytes(seed);
        return new TerrainCrypto(seed);
    }

    private static TerrainCrypto recovered() {
        LOGGER.error("Invalid secure terrain metadata was discarded because -D{}=true. A new terrain master seed will be generated and terrain seams may occur.", LeafConstants.DISABLE_SECURE_SEED_INTEGRITY_CHECK_FLAG);
        return random();
    }

    public static TerrainCrypto fromHex(final String value) {
        return new TerrainCrypto(parseMasterSeed(value));
    }

    private static DataResult<TerrainCrypto> decode(final Serialized serialized) {
        if (serialized.schemaVersion != SCHEMA_VERSION) {
            return DataResult.error(() -> "Unknown secure terrain schema version: " + serialized.schemaVersion);
        }
        if (!ALGORITHM.equals(serialized.algorithm)) {
            return DataResult.error(() -> "Unknown secure terrain algorithm: " + serialized.algorithm);
        }

        try {
            return DataResult.success(new TerrainCrypto(parseMasterSeed(serialized.masterSeed)));
        } catch (IllegalArgumentException ex) {
            return DataResult.error(ex::getMessage);
        }
    }

    private static byte[] parseMasterSeed(final String value) {
        if (value.length() != MASTER_SEED_BYTES * 2) {
            throw new IllegalArgumentException("Secure terrain master seed must contain exactly " + (MASTER_SEED_BYTES * 2) + " hexadecimal characters");
        }

        try {
            return HEX_FORMAT.parseHex(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Secure terrain master seed contains non-hexadecimal characters", ex);
        }
    }

    public byte[] masterSeed() {
        return this.masterSeed.clone();
    }

    public String masterSeedHex() {
        return HEX_FORMAT.formatHex(this.masterSeed);
    }

    private record Serialized(int schemaVersion, String algorithm, String masterSeed) {
    }
}
