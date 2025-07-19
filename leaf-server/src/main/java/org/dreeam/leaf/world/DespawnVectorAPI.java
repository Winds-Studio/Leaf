package org.dreeam.leaf.world;
import jdk.incubator.vector.*;

import static org.dreeam.leaf.world.DespawnMap.*;

public final class DespawnVectorAPI {

    private DespawnVectorAPI() {
    }

    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;
    static final int DOUBLE_VECTOR_LENGTH = DOUBLE_SPECIES.length();

    static double nearest(final int[] stack,
                          final double[] nsl,
                          final long[] nll,
                          final long[] nbl,
                          final double[] bxl, final double[] byl, final double[] bzl,
                          final double tx, final double ty, final double tz,
                          double dist) {
        final DoubleVector vtx = DoubleVector.broadcast(DOUBLE_SPECIES, tx);
        final DoubleVector vty = DoubleVector.broadcast(DOUBLE_SPECIES, ty);
        final DoubleVector vtz = DoubleVector.broadcast(DOUBLE_SPECIES, tz);
        int i = 0;
        stack[i++] = 0;
        while (i != 0) {
            final int idx = stack[--i];
            final long data = nll[idx];
            if (data != LEAF) {
                final long axis = data & AXIS_MASK;
                final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nsl[idx];
                final boolean negative = (Double.doubleToRawLongBits(delta) & 0x8000_0000_0000_0000L) == 0x8000_0000_0000_0000L;
                final long sMask = negative ? -1L : 0L;
                final boolean leftValid = (data & LEFT_MASK) != LEFT_MASK;
                final boolean rightValid = (data & RIGHT_MASK) != RIGHT_MASK;
                final long node = sMask & (data & LEFT_MASK) >>> 2 | ~sMask & data >>> 32;
                final long other = sMask & data >>> 32 | ~sMask & (data & LEFT_MASK) >>> 2;
                if ((negative & leftValid) | (!negative & rightValid)) {
                    stack[i++] = (int) node;
                }
                if ((!negative & leftValid) | (negative & rightValid) && delta * delta < dist) {
                    stack[i++] = (int) other;
                }
            } else {
                final long bucket = nbl[idx];
                final int start = (int) (bucket >>> 32);
                final int bucketSize = (int) (bucket & 0xffffffffL);
                if (DOUBLE_VECTOR_LENGTH == bucketSize) {
                    final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, start).sub(vtx);
                    final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, start).sub(vty);
                    final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, start).sub(vtz);
                    final DoubleVector vDist = FMA ?
                        vdz.fma(vdz, vdy.fma(vdy, vdx.mul(vdx))) :
                        vdx.mul(vdx).add(vdy.mul(vdy)).add(vdz.mul(vdz));
                    dist = Math.min(dist, vDist.reduceLanes(VectorOperators.MIN));
                } else if (DOUBLE_VECTOR_LENGTH > 4 && bucketSize >= 4) {
                    final VectorMask<Double> mask = DOUBLE_SPECIES.indexInRange(0, bucketSize);
                    final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, start, mask).sub(vtx);
                    final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, start, mask).sub(vty);
                    final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, start, mask).sub(vtz);
                    final DoubleVector vDist = FMA ?
                        vdz.fma(vdz, vdy.fma(vdy, vdx.mul(vdx))) :
                        vdx.mul(vdx).add(vdy.mul(vdy)).add(vdz.mul(vdz));
                    dist = Math.min(dist, vDist.reduceLanes(VectorOperators.MIN));
                } else {
                    final int end = start + bucketSize;
                    for (int j = start; j < end; j++) {
                        final double dx = bxl[j] - tx;
                        final double dy = byl[j] - ty;
                        final double dz = bzl[j] - tz;
                        final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                        dist = Math.min(dist, d2);
                    }
                }
            }
        }
        return dist;
    }
}
