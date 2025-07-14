package org.dreeam.leaf.world;
import jdk.incubator.vector.*;
import org.dreeam.leaf.util.queue.IntDeque;

import static org.dreeam.leaf.world.DespawnMap.*;

public final class DespawnVectorAPI {

    private DespawnVectorAPI() {
    }

    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;
    static final int DOUBLE_VECTOR_LENGTH = DOUBLE_SPECIES.length();

    static double nearest(final IntDeque search,
                          final double[] nxl, final double[] nyl, final double[] nzl,
                          final int[] axl, final int[] nll, final int[] nrl,
                          final boolean[] leaf,
                          final double[] bxl, final double[] byl, final double[] bzl,
                          final int[] nbi, final int[] nbs,
                          final double tx, final double ty, final double tz) {
        DoubleVector vMinDist = DoubleVector.broadcast(DOUBLE_SPECIES, Double.POSITIVE_INFINITY);
        double dist = Double.POSITIVE_INFINITY;
        final DoubleVector vtx = DoubleVector.broadcast(DOUBLE_SPECIES, tx);
        final DoubleVector vty = DoubleVector.broadcast(DOUBLE_SPECIES, ty);
        final DoubleVector vtz = DoubleVector.broadcast(DOUBLE_SPECIES, tz);
        while (!search.isEmpty()) {
            final int idx = search.dequeueFront();
            if (leaf[idx]) {
                int bucket = nbi[idx];
                final int end = bucket + nbs[idx];
                final int bucketSize = end - bucket;
                int i = 0;
                if (DOUBLE_VECTOR_LENGTH == bucketSize) {
                    final int start = bucket + i;
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
                    final DoubleVector vdx = DoubleVector.fromArray(DOUBLE_SPECIES, bxl, bucket, mask).sub(vtx);
                    final DoubleVector vdy = DoubleVector.fromArray(DOUBLE_SPECIES, byl, bucket, mask).sub(vty);
                    final DoubleVector vdz = DoubleVector.fromArray(DOUBLE_SPECIES, bzl, bucket, mask).sub(vtz);
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
                    double scalarMin = dist;
                    for (; i < bucketSize; i++) {
                        final int pointIdx = bucket + i;
                        final double dx = bxl[pointIdx] - tx;
                        final double dy = byl[pointIdx] - ty;
                        final double dz = bzl[pointIdx] - tz;
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
            } else {
                final int axis = axl[idx];
                final double delta = axis == AXIS_X ? tx - nxl[idx] : axis == AXIS_Y ? ty - nyl[idx] : tz - nzl[idx];
                final int s = (int) (Double.doubleToRawLongBits(delta) >>> 63);
                final int l = nll[idx], r = nrl[idx];
                final int nearIdx = s * l + (1 - s) * r;
                final int farIdx = s * r + (1 - s) * l;
                if (nearIdx != ROOT) {
                    search.enqueueBack(nearIdx);
                }
                if (farIdx != ROOT && delta * delta < dist) {
                    search.enqueueBack(farIdx);
                }
            }
        }
        search.clear();
        return vMinDist.reduceLanes(VectorOperators.MIN);
    }
}
