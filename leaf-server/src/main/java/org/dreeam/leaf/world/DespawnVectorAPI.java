package org.dreeam.leaf.world;
import jdk.incubator.vector.*;

import static org.dreeam.leaf.world.DespawnMap.*;

public final class DespawnVectorAPI {

    private DespawnVectorAPI() {
    }

    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;
    static final int DOUBLE_VECTOR_LENGTH = DOUBLE_SPECIES.length();

    static double nearest(final int[] stack,
                          final double[] nxl, final double[] nyl, final double[] nzl,
                          final long[] nll, final long[] nbl,
                          final double[] bxl, final double[] byl, final double[] bzl,
                          final double tx, final double ty, final double tz) {
        DoubleVector vMinDist = DoubleVector.broadcast(DOUBLE_SPECIES, Double.POSITIVE_INFINITY);
        double dist = Double.POSITIVE_INFINITY;
        final DoubleVector vtx = DoubleVector.broadcast(DOUBLE_SPECIES, tx);
        final DoubleVector vty = DoubleVector.broadcast(DOUBLE_SPECIES, ty);
        final DoubleVector vtz = DoubleVector.broadcast(DOUBLE_SPECIES, tz);
        int i = 0;
        stack[i++] = 0;
        while (i != 0) {
            final int idx = stack[--i];
            final long bucket = nbl[idx];
            if (bucket == INTERNAL) {
                final long data = nll[idx];
                final long axis = data & 0b11;
                final double delta = axis == AXIS_X ? tx - nxl[idx] : axis == AXIS_Y ? ty - nyl[idx] : tz - nzl[idx];
                final boolean negative = (Double.doubleToRawLongBits(delta) & 0x8000_0000_0000_0000L) == 0x8000_0000_0000_0000L;
                final long sMask = negative ? -1L : 0L;
                final boolean leftValid = (data & 0xfffffffcL) != 0xfffffffcL;
                final boolean rightValid = (data & 0x3fffffff00000000L) != 0x3fffffff00000000L;
                final long node = sMask & (data & 0xfffffffcL) >>> 2 | ~sMask & data >>> 32;
                final long other = sMask & data >>> 32 | ~sMask & (data & 0xfffffffcL) >>> 2;
                if ((negative & leftValid) | (!negative & rightValid)) {
                    stack[i++] = (int) node;
                }
                if ((!negative & leftValid) | (negative & rightValid) && delta * delta < dist) {
                    stack[i++] = (int) other;
                }
            } else {
                final int start = (int) (bucket >>> 32);
                final int bucketSize = (int) (bucket & 0xffffffffL);
                if (DOUBLE_VECTOR_LENGTH == bucketSize) {
                    final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, start).sub(vtx);
                    final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, start).sub(vty);
                    final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, start).sub(vtz);
                    final DoubleVector vDist = FMA ?
                        vdz.fma(vdz, vdy.fma(vdy, vdx.mul(vdx))) :
                        vdx.mul(vdx).add(vdy.mul(vdy)).add(vdz.mul(vdz));
                    vMinDist = vMinDist.min(vDist);
                    dist = vMinDist.reduceLanes(VectorOperators.MIN);
                } else if (DOUBLE_VECTOR_LENGTH > 4 && bucketSize >= 4) {
                    VectorMask<Double> mask = DOUBLE_SPECIES.indexInRange(0, bucketSize);
                    final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, start, mask).sub(vtx);
                    final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, start, mask).sub(vty);
                    final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, start, mask).sub(vtz);
                    final DoubleVector vDist = FMA ?
                        vdz.fma(vdz, vdy.fma(vdy, vdx.mul(vdx))) :
                        vdx.mul(vdx).add(vdy.mul(vdy)).add(vdz.mul(vdz));
                    final DoubleVector newMinDist = vMinDist.min(vDist);
                    final double newDist = newMinDist.reduceLanes(VectorOperators.MIN);
                    if (newDist < dist) {
                        vMinDist = newMinDist;
                        dist = newDist;
                    }
                } else {
                    final int end = start + bucketSize;
                    double scalarMin = dist;
                    for (int j = start; j < end; j++) {
                        final double dx = bxl[j] - tx;
                        final double dy = byl[j] - ty;
                        final double dz = bzl[j] - tz;
                        final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                        if (d2 < scalarMin) {
                            scalarMin = d2;
                        }
                    }
                    if (scalarMin < dist) {
                        vMinDist = DoubleVector.broadcast(DOUBLE_SPECIES, scalarMin);
                        dist = scalarMin;
                    }
                }
            }
        }
        return vMinDist.reduceLanes(VectorOperators.MIN);
    }
}
