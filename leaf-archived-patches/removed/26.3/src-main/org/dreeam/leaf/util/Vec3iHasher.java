package org.dreeam.leaf.util;

/**
 * Hash helper for 3D integer coordinates.
 *
 * <p>The vanilla hash formula is linear in a way that can produce large
 * collision groups for structured spatial inputs, for example dense chunk
 * BlockPos sets. This hasher uses a different coordinate combination followed
 * by a small integer mix to reduce those pathological distributions.</p>
 *
 * <p>This is not intended to be universally faster than the vanilla formula:
 * random inputs and small maps may see little benefit.</p>
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
