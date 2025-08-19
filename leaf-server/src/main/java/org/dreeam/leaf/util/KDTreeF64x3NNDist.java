package org.dreeam.leaf.util;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KDTreeF64x3NNDist {

    private static final double[] EMPTY_DOUBLES = {};
    private static final long[] EMPTY_LONGS = {};
    private static final Node[] EMPTY_NODES = {};
    static final boolean FMA = true;
    private static final int LEAF_THRESHOLD = 4;
    private static final int INITIAL_CAP = 8;
    static final long LEAF = -1L;
    static final long AXIS_X = 0L;
    static final long AXIS_Y = 1L;
    static final long AXIS_Z = 2L;
    static final long LEFT_MASK = 0xfffffffcL;
    static final long RIGHT_MASK = 0x3fffffff00000000L;
    static final long OFFSET_MASK = 0x7fffffffL;
    static final long LEN_MASK = 0xF80000000L;
    static final long LEN_4_MASK = 0x200000000L;
    static final int LEN_OFFSET = 31;
    static final int INDEX_OFFSET = 36;
    static final int LEFT_CHILD_OFFSET = 2;
    static final int RIGHT_CHILD_OFFSET = 32;
    static final long AXIS_MASK = 0b11L;
    private static final boolean SIMD = SIMDDetection.isEnabled();
    private Node[] stack = EMPTY_NODES;
    private long[] search = EMPTY_LONGS;
    private double[] nsl = EMPTY_DOUBLES;
    /// Lengths(5) Offsets(31) for each player list of leaf nodes
    private long[] nbl = EMPTY_LONGS;
    /// Right(30) Left(30) Axis(2) for each internal node
    private long[] nll = EMPTY_LONGS;
    /// Nested player X coordinates of leaf nodes
    private double[] bxl = EMPTY_DOUBLES;
    /// Nested player Y coordinates of leaf nodes
    private double[] byl = EMPTY_DOUBLES;
    /// Nested player Z coordinates of leaf nodes
    private double[] bzl = EMPTY_DOUBLES;

    public void build(final double[] coordinateX, final double[] coordinateY, final double[] coordinateZ, final int[] indices) {
        int nodeLen = 0;
        int bucketLen = 0;
        final double[][] map = {coordinateX, coordinateY, coordinateZ};
        int st = 0;
        if (st == stack.length) {
            stack = new Node[INITIAL_CAP];
        }
        stack[st++] = new Node(-1, false, 0, indices.length, 0);
        while (st != 0) {
            {
                int newLen = nodeLen + 1;
                if (newLen >= nsl.length) {
                    newLen += newLen >> 1;
                    if (newLen < INITIAL_CAP) {
                        newLen = INITIAL_CAP;
                    }
                    nsl = DoubleArrays.forceCapacity(nsl, newLen, nodeLen);
                    nll = LongArrays.forceCapacity(nll, newLen, nodeLen);
                    nbl = LongArrays.forceCapacity(nbl, newLen, nodeLen);
                }
            }
            final Node n = stack[--st];
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            final int curr = nodeLen++;
            if (len <= LEAF_THRESHOLD) {
                nll[curr] = LEAF;
                nbl[curr] = (long) len << LEN_OFFSET | (long) bucketLen;

                int newLen = bucketLen + len;
                if (newLen >= bxl.length) {
                    newLen = Math.max(INITIAL_CAP, newLen + (newLen >> 1));
                    bxl = DoubleArrays.forceCapacity(bxl, newLen, bucketLen);
                    byl = DoubleArrays.forceCapacity(byl, newLen, bucketLen);
                    bzl = DoubleArrays.forceCapacity(bzl, newLen, bucketLen);
                }
                for (int i = offset, end = offset + len; i < end; i++) {
                    bxl[bucketLen] = coordinateX[indices[i]];
                    byl[bucketLen] = coordinateY[indices[i]];
                    bzl[bucketLen] = coordinateZ[indices[i]];
                    bucketLen++;
                }
            } else {
                final int axis = depth % 3 == 0 ? (int) AXIS_X : depth % 3 == 1 ? (int) AXIS_Z : (int) AXIS_Y;
                final int median = (len - 1) / 2;
                PartialSort.nthElement(indices, map[axis], offset, offset + len - 1, offset + median);
                final int pivot = indices[offset + median];
                nsl[curr] = axis == AXIS_X ? coordinateX[pivot] : axis == AXIS_Y ? coordinateY[pivot] : coordinateZ[pivot];
                nll[curr] = RIGHT_MASK | LEFT_MASK | (long) axis;
                nbl[curr] = 0L;

                if (st == stack.length || st + 1 == stack.length) {
                    final int newLen = stack.length + 2;
                    final Node[] b = new Node[Math.max(INITIAL_CAP, newLen + (newLen >> 1))];
                    System.arraycopy(stack, 0, b, 0, st);
                    stack = b;
                }
                stack[st++] = new Node(curr, false, offset + median + 1, len - median - 1, depth + 1);
                stack[st++] = new Node(curr, true, offset, median + 1, depth + 1);
            }
            if (n.parent >= 0) {
                if (n.left) {
                    nll[n.parent] &= AXIS_MASK | RIGHT_MASK;
                    nll[n.parent] |= (long) curr << LEFT_CHILD_OFFSET;
                } else {
                    nll[n.parent] &= AXIS_MASK | LEFT_MASK;
                    nll[n.parent] |= (long) curr << RIGHT_CHILD_OFFSET;
                }
            }
        }

        if (search.length < Math.max(64, nodeLen * 2)) {
            search = new long[Math.max(64, nodeLen * 2)];
        }
        search[0] = indices.length == 0 ? -1 : 0;
    }

    public double nearest(final double tx, final double ty, final double tz, double dist) {
        final long[] stack = this.search;
        final double[] nsl = this.nsl;
        final long[] nll = this.nll;
        final long[] nbl = this.nbl;
        final double[] bxl = this.bxl;
        final double[] byl = this.byl;
        final double[] bzl = this.bzl;
        if (stack[0] == -1L) {
            return Double.POSITIVE_INFINITY;
        }
        if (SIMD) {
            return KDTreeF64x3NNDistVectorAPI.nearest(search, nsl, nll, nbl, bxl, byl, bzl, tx, ty, tz, dist);
        }
        int i = 0;
        stack[i++] = nbl[0];
        while (i != 0) {
            final long data = stack[--i];
            if ((data & LEN_MASK) != 0L) {
                int start = (int) (data & OFFSET_MASK);
                final int end = start + (int) ((data & LEN_MASK) >>> LEN_OFFSET);
                for (; start != end; start++) {
                    final double dx = bxl[start] - tx;
                    final double dy = byl[start] - ty;
                    final double dz = bzl[start] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    dist = Math.min(dist, d2);
                }
            } else {
                final int idx = (int) (data >>> INDEX_OFFSET);
                final long axis = data & AXIS_MASK;
                final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nsl[idx];
                final long n = nll[idx];
                final boolean negative = Double.doubleToRawLongBits(delta) < 0L;
                final boolean hasLeft = (n & LEFT_MASK) != LEFT_MASK;
                final boolean hasRight = n >= 0L;
                final long left = (n & LEFT_MASK) >>> LEFT_CHILD_OFFSET;
                final long right = n >>> RIGHT_CHILD_OFFSET;
                if (negative) {
                    if (hasRight && delta * delta < dist) {
                        stack[i++] = nbl[(int) right] | (right << INDEX_OFFSET);
                    }
                    if (hasLeft) {
                        stack[i++] = nbl[(int) left] | (left << INDEX_OFFSET);
                    }
                } else {
                    if (hasLeft && delta * delta < dist) {
                        stack[i++] = nbl[(int) left] | (left << INDEX_OFFSET);
                    }
                    if (hasRight) {
                        stack[i++] = nbl[(int) right] | (right << INDEX_OFFSET);
                    }
                }
            }
        }
        return dist;
    }

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }
}
