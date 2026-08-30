package su.plo.matter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.dreeam.leaf.config.modules.misc.SecureSeed;
import org.jspecify.annotations.Nullable;

public final class TerrainCryptoContext {
    private final byte[] dimensionKey;

    private TerrainCryptoContext(final byte[] dimensionKey) {
        this.dimensionKey = dimensionKey.clone();
    }

    public static @Nullable TerrainCryptoContext create(final WorldOptions options, final ResourceKey<Level> dimension) {
        if (!SecureSeed.isSecureTerrainEnabled()) {
            return null;
        }

        TerrainCrypto terrainCrypto = options.terrainCrypto();
        if (terrainCrypto == null) {
            throw new IllegalStateException("Secure terrain is enabled but the world has no terrain master seed");
        }

        byte[] dimensionKey = TerrainHashing.derive(
            terrainCrypto.masterSeed(),
            TerrainHashing.DIMENSION,
            TerrainHashing.stringContext(dimension.identifier().toString())
        );
        return new TerrainCryptoContext(dimensionKey);
    }

    public RandomSource initializationRandom(final String domain) {
        return new HmacSha256RandomSource(this.domainKey(domain));
    }

    public PositionalRandomFactory runtimeFactory(final String domain) {
        byte[] domainKey = this.domainKey(domain);
        byte[] factoryKey = TerrainHashing.derive(domainKey, TerrainHashing.FAST_FACTORY, new byte[0]);
        int attempt = 0;
        while (true) {
            long seedLo = TerrainHashing.readLong(factoryKey, 0);
            long seedHi = TerrainHashing.readLong(factoryKey, Long.BYTES);
            if ((seedLo | seedHi) != 0L) { // probability ~2^-128, how could that be possible
                return new SecureRuntimeRandomFactory(seedLo, seedHi);
            }

            seedLo = TerrainHashing.readLong(factoryKey, Long.BYTES * 2);
            seedHi = TerrainHashing.readLong(factoryKey, Long.BYTES * 3);
            if ((seedLo | seedHi) != 0L) {
                return new SecureRuntimeRandomFactory(seedLo, seedHi);
            }

            if (attempt == Integer.MAX_VALUE) {
                throw new IllegalStateException("Unable to derive a non-zero secure terrain runtime seed");
            }
            factoryKey = TerrainHashing.derive(domainKey, TerrainHashing.FAST_FACTORY_RETRY, TerrainHashing.intContext(++attempt));
        }
    }

    private byte[] domainKey(final String domain) {
        return TerrainHashing.derive(this.dimensionKey, TerrainHashing.DOMAIN, TerrainHashing.stringContext(domain));
    }

    private static final class SecureRuntimeRandomFactory implements PositionalRandomFactory {
        private final long seedLo;
        private final long seedHi;

        private SecureRuntimeRandomFactory(final long seedLo, final long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            long positionalSeed = Mth.getSeed(x, y, z);
            return new XoroshiroRandomSource(positionalSeed ^ this.seedLo, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(final String name) {
            RandomSupport.Seed128bit seed = RandomSupport.seedFromHashOf(name);
            return new XoroshiroRandomSource(seed.xor(this.seedLo, this.seedHi));
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi);
        }

        @Override
        public void parityConfigString(final StringBuilder sb) {
            sb.append("SecureRuntimeRandomFactory");
        }
    }
}
