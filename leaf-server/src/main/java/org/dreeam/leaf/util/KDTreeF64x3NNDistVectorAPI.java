package org.dreeam.leaf.util;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import static org.dreeam.leaf.util.KDTreeF64x3NNDist.*;

public final class KDTreeF64x3NNDistVectorAPI {

    private KDTreeF64x3NNDistVectorAPI() {
    }

    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_256;

    static double nearest(final long[] stack,
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
        stack[i++] = nbl[0];
        while (i != 0) {
            final long data = stack[--i];
            final long len = data & LEN_MASK;
            if (len == LEN_4_MASK) {
                final int start = (int) (data & OFFSET_MASK);
                final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, start).sub(vtx);
                final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, start).sub(vty);
                final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, start).sub(vtz);
                final DoubleVector vDist = FMA ?
                    vdz.fma(vdz, vdy.fma(vdy, vdx.mul(vdx))) :
                    vdx.mul(vdx).add(vdy.mul(vdy)).add(vdz.mul(vdz));
                dist = Math.min(dist, vDist.reduceLanes(VectorOperators.MIN));
            } else if (len != 0L) {
                int start = (int) (data & OFFSET_MASK);
                final int end = start + (int) (len >>> LEN_OFFSET);
                for (; start != end; start++) {
                    final double dx = bxl[start] - tx;
                    final double dy = byl[start] - ty;
                    final double dz = bzl[start] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    dist = Math.min(dist, d2);
                }
            } else {
                i = nnInternal(tx, ty, tz, dist, data, nsl, nll, stack, i, nbl);
            }
        }
        return dist;
    }
}
