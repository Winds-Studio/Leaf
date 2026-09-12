package su.plo.matter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.dreeam.leaf.config.modules.misc.SecureSeed;
import org.jspecify.annotations.Nullable;

public final class TerrainCryptoContext {
    private final Blake2b.PreparedKey dimensionKey;

    private TerrainCryptoContext(final Blake2b.PreparedKey dimensionKey) {
        this.dimensionKey = dimensionKey;
    }

    public static @Nullable TerrainCryptoContext create(final WorldOptions options, final ResourceKey<Level> dimension) {
        if (!SecureSeed.isSecureTerrainEnabled()) {
            return null;
        }

        TerrainCrypto terrainCrypto = options.terrainCrypto();
        if (terrainCrypto == null) {
            throw new IllegalStateException("Secure terrain is enabled but the world has no terrain master seed");
        }

        Blake2b.PreparedKey dimensionKey = TerrainHashing.derivePrepared(
            terrainCrypto.masterSeed(),
            TerrainHashing.DIMENSION,
            TerrainHashing.stringToBytes(dimension.identifier().toString())
        );
        return new TerrainCryptoContext(dimensionKey);
    }

    public RandomSource initializationRandom(final String domain) {
        return new Blake2bRandomSource(this.domainKey(domain));
    }

    public PositionalRandomFactory runtimeFactory(final String domain) {
        return new SecureRuntimeRandomFactory(this.domainKey(domain));
    }

    private Blake2b.PreparedKey domainKey(final String domain) {
        return TerrainHashing.derivePrepared(this.dimensionKey, TerrainHashing.DOMAIN, TerrainHashing.stringToBytes(domain));
    }

    private static final class SecureRuntimeRandomFactory implements PositionalRandomFactory {
        private static final Blake2b.Seed128Factory<RandomSource> BLAKE2B_XOROSHIRO_FACTORY = XoroshiroRandomSource::new;
        private static final ChaCha12.Seed128Factory<RandomSource> CHACHA12_XOROSHIRO_FACTORY = XoroshiroRandomSource::new;
        private final Blake2b.PreparedKey hashKey;
        private final ChaCha12.PreparedKey positionKey;
        private final ChaCha12.PreparedKey seedKey;

        private SecureRuntimeRandomFactory(final Blake2b.PreparedKey key) {
            this.hashKey = key;
            this.positionKey = TerrainHashing.deriveChaChaKey(key, TerrainHashing.RUNTIME_AT);
            this.seedKey = TerrainHashing.deriveChaChaKey(key, TerrainHashing.RUNTIME_SEED);
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return TerrainHashing.deriveRuntimePosition(this.positionKey, x, y, z, CHACHA12_XOROSHIRO_FACTORY);
        }

        @Override
        public RandomSource fromHashOf(final String name) {
            return TerrainHashing.deriveRuntimeHash(this.hashKey, name, BLAKE2B_XOROSHIRO_FACTORY);
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return TerrainHashing.deriveRuntimeSeed(this.seedKey, seed, CHACHA12_XOROSHIRO_FACTORY);
        }

        @Override
        public void parityConfigString(final StringBuilder sb) {
            sb.append("SecureChaCha12RuntimeRandomFactory");
        }
    }
}
