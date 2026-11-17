package org.dreeam.leaf.util.map;

public final class SectionVisitSet {
    private final int minX;
    private final int minY;
    private final int minZ;

    private final int sizeX;
    private final int sizeY;

    private final long[] bits;

    public SectionVisitSet(
        final int minX,
        final int minY,
        final int minZ,
        final int maxX,
        final int maxY,
        final int maxZ
    ) {
        final long sizeX = (long) maxX - (long) minX + 1L;
        final long sizeY = (long) maxY - (long) minY + 1L;
        final long sizeZ = (long) maxZ - (long) minZ + 1L;

        if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) {
            throw new IllegalArgumentException("Invalid section bounds");
        }

        final long sectionCount;
        try {
            sectionCount = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (final ArithmeticException ex) {
            throw new IllegalArgumentException("Section bounds are too large", ex);
        }

        if (sectionCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Section search size is too large: " + sectionCount);
        }

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.sizeX = (int) sizeX;
        this.sizeY = (int) sizeY;

        this.bits = new long[(int) ((sectionCount + 63L) >>> 6)];
    }

    /**
     * @return true if this section was not visite before
     */
    public boolean mark(
        final int x,
        final int y,
        final int z
    ) {
        final int localX = x - this.minX;
        final int localY = y - this.minY;
        final int localZ = z - this.minZ;

        /*
         * Layout:
         *
         * Z
         *  └ X
         *      └ Y
         */
        final int index = ((localZ * this.sizeX + localX) * this.sizeY) + localY;

        final int wordIndex = index >>> 6;
        final long mask = 1L << (index & 63);

        final long previous = this.bits[wordIndex];

        if ((previous & mask) != 0L) {
            return false;
        }

        this.bits[wordIndex] = previous | mask;
        return true;
    }
}
