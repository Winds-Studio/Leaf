package org.dreeam.leaf.world;
import jdk.incubator.vector.*;
import org.dreeam.leaf.util.queue.IntDeque;

import static org.dreeam.leaf.world.DespawnMap.*;

public final class DespawnVectorAPI {

    private DespawnVectorAPI() {
    }

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    static final int VECTOR_LENGTH = SPECIES.length();

    static double nearest(final IntDeque search,
                          final double[] nxl, final double[] nyl, final double[] nzl,
                          final int[] axl, final int[] nll, final int[] nrl,
                          final boolean[] leaf,
                          final double[] bxl, final double[] byl, final double[] bzl,
                          final int[] nbi, final int[] nbs,
                          final double tx, final double ty, final double tz) {
        DoubleVector vMinDist = DoubleVector.broadcast(SPECIES, Double.POSITIVE_INFINITY);
        double dist = Double.POSITIVE_INFINITY;
        final DoubleVector vTx = DoubleVector.broadcast(SPECIES, tx);
        final DoubleVector vTy = DoubleVector.broadcast(SPECIES, ty);
        final DoubleVector vTz = DoubleVector.broadcast(SPECIES, tz);
        while (!search.isEmpty()) {
            final int idx = search.poll();
            if (leaf[idx]) {
                int bucket = nbi[idx];
                final int end = bucket + nbs[idx];
                final int bucketSize = end - bucket;
                int i = 0;
                if (VECTOR_LENGTH == bucketSize) {
                    final int start = bucket + i;
                    final DoubleVector vDx = DoubleVector.fromArray(SPECIES, bxl, start).sub(vTx);
                    final DoubleVector vDy = DoubleVector.fromArray(SPECIES, byl, start).sub(vTy);
                    final DoubleVector vDz = DoubleVector.fromArray(SPECIES, bzl, start).sub(vTz);
                    final DoubleVector vDist = FMA ?
                        vDz.fma(vDz, vDy.fma(vDy, vDx.mul(vDx))) :
                        vDx.mul(vDx).add(vDy.mul(vDy)).add(vDz.mul(vDz));
                    vMinDist = vMinDist.min(vDist);
                    dist = vMinDist.reduceLanes(VectorOperators.MIN);
                } else if (VECTOR_LENGTH > 4 && bucketSize >= 4) {
                    VectorMask<Double> mask = SPECIES.indexInRange(0, bucketSize);
                    final DoubleVector vDx = DoubleVector.fromArray(SPECIES, bxl, bucket, mask).sub(vTx);
                    final DoubleVector vDy = DoubleVector.fromArray(SPECIES, byl, bucket, mask).sub(vTy);
                    final DoubleVector vDz = DoubleVector.fromArray(SPECIES, bzl, bucket, mask).sub(vTz);
                    final DoubleVector vDist = FMA ?
                        vDz.fma(vDz, vDy.fma(vDy, vDx.mul(vDx))) :
                        vDx.mul(vDx).add(vDy.mul(vDy)).add(vDz.mul(vDz));
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
                        vMinDist = DoubleVector.broadcast(SPECIES, scalarMin);
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
                    search.push(nearIdx);
                }
                if (farIdx != ROOT && delta * delta < dist) {
                    search.push(farIdx);
                }
            }
        }
        search.clear();
        return vMinDist.reduceLanes(VectorOperators.MIN);
    }
}
