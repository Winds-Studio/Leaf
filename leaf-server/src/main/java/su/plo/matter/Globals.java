package su.plo.matter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Optional;

public class Globals {

    public static final int WORLD_SEED_LONGS = 16;
    public static final int WORLD_SEED_BITS = WORLD_SEED_LONGS * 64;

    private static final int CUSTOM_DIMENSION_ID = -1;
    private static final DimensionSeed OVERWORLD_DIMENSION_SEED = new DimensionSeed(0, 0L, 0L);
    private static final DimensionSeed NETHER_DIMENSION_SEED = new DimensionSeed(1, 0L, 0L);
    private static final DimensionSeed END_DIMENSION_SEED = new DimensionSeed(2, 0L, 0L);
    private static final ThreadLocal<long[]> WORLD_SEED = ThreadLocal.withInitial(() -> new long[WORLD_SEED_LONGS]);
    private static final ThreadLocal<DimensionSeed> DIMENSION_SEED = ThreadLocal.withInitial(() -> OVERWORLD_DIMENSION_SEED);

    public record DimensionSeed(int legacyId, long seedLo, long seedHi) {
    }

    public enum Salt {
        UNDEFINED,
        BASTION_FEATURE,
        WOODLAND_MANSION_FEATURE,
        MINESHAFT_FEATURE,
        BURIED_TREASURE_FEATURE,
        NETHER_FORTRESS_FEATURE,
        PILLAGER_OUTPOST_FEATURE,
        GEODE_FEATURE,
        NETHER_FOSSIL_FEATURE,
        OCEAN_MONUMENT_FEATURE,
        RUINED_PORTAL_FEATURE,
        POTENTIONAL_FEATURE,
        GENERATE_FEATURE,
        JIGSAW_PLACEMENT,
        STRONGHOLDS,
        POPULATION,
        DECORATION,
        SLIME_CHUNK
    }

    public static void setupGlobals(ServerLevel world) {
        if (!org.dreeam.leaf.config.modules.misc.SecureSeed.enabled) return;

        copyWorldSeed(world, WORLD_SEED.get());
        DIMENSION_SEED.set(world.matter$dimensionSeed);
    }

    public static DimensionSeed createDimensionSeed(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return OVERWORLD_DIMENSION_SEED;
        if (dimension == Level.NETHER) return NETHER_DIMENSION_SEED;
        if (dimension == Level.END) return END_DIMENSION_SEED;

        RandomSupport.Seed128bit seed = RandomSequence.seedForKey(dimension.identifier());
        return new DimensionSeed(CUSTOM_DIMENSION_ID, seed.seedLo(), seed.seedHi());
    }

    static void copyWorldSeed(long[] destination) {
        System.arraycopy(WORLD_SEED.get(), 0, destination, 0, WORLD_SEED_LONGS);
    }

    static void copyWorldSeed(ServerLevel world, long[] destination) {
        System.arraycopy(world.worldGenSettings.options().featureSeed(), 0, destination, 0, WORLD_SEED_LONGS);
    }

    static DimensionSeed dimensionSeed() {
        return DIMENSION_SEED.get();
    }

    public static long[] createRandomWorldSeed() {
        long[] seed = new long[WORLD_SEED_LONGS];
        SecureRandom rand = new SecureRandom();
        for (int i = 0; i < WORLD_SEED_LONGS; i++) {
            seed[i] = rand.nextLong();
        }
        return seed;
    }

    // 1024-bit string -> 16 * 64 long[]
    public static Optional<long[]> parseSeed(String seedStr) {
        if (seedStr.isEmpty()) return Optional.empty();

        if (seedStr.length() != WORLD_SEED_BITS) {
            throw new IllegalArgumentException("Secure seed length must be " + WORLD_SEED_BITS + "-bit but found " + seedStr.length() + "-bit.");
        }

        long[] seed = new long[WORLD_SEED_LONGS];

        for (int i = 0; i < WORLD_SEED_LONGS; i++) {
            int start = i * 64;
            int end = start + 64;
            String seedSection = seedStr.substring(start, end);

            BigInteger seedInDecimal = new BigInteger(seedSection, 2);
            seed[i] = seedInDecimal.longValue();
        }

        return Optional.of(seed);
    }

    // 16 * 64 long[] -> 1024-bit string
    public static String seedToString(long[] seed) {
        StringBuilder sb = new StringBuilder();

        for (long longV : seed) {
            // Convert to 64-bit binary string per long
            // Use format to keep 64-bit length, and use 0 to complete space
            String binaryStr = String.format("%64s", Long.toBinaryString(longV)).replace(' ', '0');

            sb.append(binaryStr);
        }

        return sb.toString();
    }
}
