package org.dreeam.leaf.util;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KDTreeF64x3NNDist {

    private static final boolean SIMD = SIMDDetection.isEnabled();
    static final boolean FMA = Boolean.getBoolean("Leaf.enableFMA");

    private static final double[] EMPTY_DOUBLES = {};
    private static final long[] EMPTY_LONGS = {};
    private static final Node[] EMPTY_NODES = {};

    private static final long AXIS_X = 0L;
    private static final long AXIS_Y = 1L;
    private static final long AXIS_Z = 2L;

    private static final int LEAF_THRESHOLD = 4;
    private static final int INITIAL_CAP = 8;
    private static final int ROOT = -1;
    /// sentinel value for empty on search
    private static final long NIL = -1L;

    private static final long AXIS_MASK = 0b11L;
    private static final int LEFT_CHILD_OFFSET = 2;
    private static final long LEFT_MASK = 0xffff_fffcL;
    private static final long RIGHT_MASK = 0x3fff_ffff_0000_0000L;
    private static final int RIGHT_CHILD_OFFSET = LEFT_CHILD_OFFSET + 30;

    static final long OFFSET_MASK = 0x7fff_ffffL;
    static final long LEN_MASK = 0xf_8000_0000L;
    static final long LEN_4_MASK = 0x2_0000_0000L;
    static final int LEN_OFFSET = 31;
    private static final int INDEX_OFFSET = 36;

    private Node[] stack = EMPTY_NODES;
    private long[] search = EMPTY_LONGS;
    /// Split value for each internal node
    private double[] nsl = EMPTY_DOUBLES;
    /// Lengths(5) Offsets(31) for each leaf node
    private long[] nbl = EMPTY_LONGS;
    /// Right(30) Left(30) Axis(2) for each internal node
    private long[] nll = EMPTY_LONGS;
    /// Nested X of leaf nodes
    private double[] bxl = EMPTY_DOUBLES;
    /// Nested Y of leaf nodes
    private double[] byl = EMPTY_DOUBLES;
    /// Nested Z of leaf nodes
    private double[] bzl = EMPTY_DOUBLES;

    public void build(final double[][] coords, final int[] indices) {
        int st = 0;
        growCon(st);
        stack[st++] = new Node(ROOT, false, 0, indices.length, 0);
        int nodeLen = 0;
        int bucketLen = 0;
        while (st != 0) {
            growNode(nodeLen);
            final Node n = stack[--st];
            final int curr = nodeLen++;
            if (n.len() <= LEAF_THRESHOLD) {
                nll[curr] = RIGHT_MASK | LEFT_MASK;
                nbl[curr] = (long) n.len() << LEN_OFFSET | bucketLen;

                growBk(bucketLen, n.len());
                bucketLen = copyCoords(coords, indices, n.offset(), n.len(), bucketLen);
            } else {
                final int axis = reorderAxis(n.depth());
                final int med = (n.len() - 1) / 2;
                PartialSort.nthElement(indices, coords[axis], n.offset(), n.offset() + n.len() - 1, n.offset() + med);
                nsl[curr] = coords[axis][indices[n.offset() + med]];
                nll[curr] = RIGHT_MASK | LEFT_MASK | axis;
                nbl[curr] = 0L;

                growCon(st);
                stack[st++] = new Node(curr, false, n.offset() + med + 1, n.len() - med - 1, n.depth() + 1);
                stack[st++] = new Node(curr, true, n.offset(), med + 1, n.depth() + 1);
            }
            setChild(n.parent(), n.left(), curr);
        }

        setSearch(indices, nodeLen);
    }

    private void setChild(final int parent, final boolean left, final long curr) {
        if (parent == ROOT) {
            return;
        }
        if (left) {
            nll[parent] &= AXIS_MASK | RIGHT_MASK;
            nll[parent] |= curr << LEFT_CHILD_OFFSET;
        } else {
            nll[parent] &= AXIS_MASK | LEFT_MASK;
            nll[parent] |= curr << RIGHT_CHILD_OFFSET;
        }
    }

    private static int reorderAxis(final int depth) {
        final int r = depth % 3;
        return r == 0 ? (int) AXIS_X : r == 1 ? (int) AXIS_Z : (int) AXIS_Y;
    }

    private int copyCoords(final double[][] coords, final int[] indices, final int offset, final int len, int bucketLen) {
        final double[] x = coords[(int) AXIS_X];
        final double[] y = coords[(int) AXIS_Y];
        final double[] z = coords[(int) AXIS_Z];
        for (int i = offset, end = offset + len; i < end; i++) {
            final int j = indices[i];
            bxl[bucketLen] = x[j];
            byl[bucketLen] = y[j];
            bzl[bucketLen] = z[j];
            bucketLen++;
        }
        return bucketLen;
    }

    private void setSearch(final int[] indices, final int nodeLen) {
        if (search.length < Math.max(64, nodeLen * 2)) {
            search = new long[Math.max(64, nodeLen * 2)];
        }
        search[0] = indices.length == 0 ? NIL : 0L;
    }

    private void growCon(final int st) {
        if (st != stack.length && st + 1 != stack.length) {
            return;
        }
        final int newLen = stack.length + 2;
        final Node[] b = new Node[Math.max(INITIAL_CAP, newLen + (newLen >> 1))];
        System.arraycopy(stack, 0, b, 0, st);
        stack = b;
    }

    private void growBk(final int bucketLen, final int len) {
        int newLen = bucketLen + len;
        if (newLen < bxl.length) {
            return;
        }
        newLen = Math.max(INITIAL_CAP, newLen + (newLen >> 1));
        bxl = DoubleArrays.forceCapacity(bxl, newLen, bucketLen);
        byl = DoubleArrays.forceCapacity(byl, newLen, bucketLen);
        bzl = DoubleArrays.forceCapacity(bzl, newLen, bucketLen);
    }

    private void growNode(final int nodeLen) {
        int newLen = nodeLen + 1;
        if (newLen < nsl.length) {
            return;
        }
        newLen += newLen >> 1;
        if (newLen < INITIAL_CAP) {
            newLen = INITIAL_CAP;
        }
        nsl = DoubleArrays.forceCapacity(nsl, newLen, nodeLen);
        nll = LongArrays.forceCapacity(nll, newLen, nodeLen);
        nbl = LongArrays.forceCapacity(nbl, newLen, nodeLen);
    }

    public double nearest(final double tx, final double ty, final double tz, double dist) {
        final long[] stack = this.search;
        final double[] nsl = this.nsl;
        final long[] nll = this.nll;
        final long[] nbl = this.nbl;
        final double[] bxl = this.bxl;
        final double[] byl = this.byl;
        final double[] bzl = this.bzl;
        if (stack.length == 0 || stack[0] == NIL) {
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
                i = nnInternal(tx, ty, tz, dist, data, nsl, nll, stack, i, nbl);
            }
        }
        return dist;
    }

    static int nnInternal(final double tx,
                          final double ty,
                          final double tz,
                          final double dist,
                          final long data,
                          final double[] nsl,
                          final long[] nll,
                          final long[] stack,
                          int i,
                          final long[] nbl) {
        final int idx = (int) (data >>> INDEX_OFFSET);
        final long axis = data & AXIS_MASK;
        final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nsl[idx];
        final long n = nll[idx];
        final boolean hasLeft = (n & LEFT_MASK) != LEFT_MASK;
        final boolean hasRight = n >= 0L;
        final long left = (n & LEFT_MASK) >>> LEFT_CHILD_OFFSET;
        final long right = n >>> RIGHT_CHILD_OFFSET;
        if (delta < 0.0) {
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
        return i;
    }

    private record Node(int parent, boolean left, int offset, int len, int depth) {
    }
}
