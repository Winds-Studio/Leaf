package org.dreeam.leaf.util;

/**
 * A high-quality 3D coordinate hasher that prioritizes distribution over raw instruction count.
 * <p>
 * This implementation trades a marginal amount of calculation speed for extremely low hash collision rates,
 * significantly improving the performance of large HashMaps.
 */
public final class Vec3iHasher {

    // See https://en.wikipedia.org/wiki/Tiny_Encryption_Algorithm
    // 2^32 * (sqrt(5) - 1) / 2
    private static final int INT_PHI = 0x9E3779B9;

    private Vec3iHasher() {
    }

    public static int hash(int x, int y, int z) {
        final int hash = (x * 31337 + y * 961 + z) * INT_PHI;
        return hash ^ (hash >>> 16);
    }
}
