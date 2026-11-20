package org.dreeam.leaf.util;

public final class KDTree2D {

    private static final double[] EMPTY_DOUBLES = {};
    private static final int[] EMPTY_INTS = {};
    private static final boolean ENABLE_FMA = LeafConstants.ENABLE_FMA;

    private static final int INITIAL_CAPACITY = 8;

    /// indicate leaf node on [#nrl]
    private static final int LEAF = -1;
    private static final int MASK = 0x7FFF_FFFF;
    private static final int BITS = 31;

    private int len;
    /// [#LEAF] for leaf / right node index for internal
    private int[] nrl = EMPTY_INTS;
    // x coordinate for leaf / split for internal
    private double[] nxl = EMPTY_DOUBLES;
    // y coordinate for leaf
    private double[] nyl = EMPTY_DOUBLES;
    // index for leaf
    private int[] nil = EMPTY_INTS;

    private int[] arr = EMPTY_INTS;

    public void build(final double[][] coords, final int[] indices) {
        if (indices.length == 0 || coords.length != 2) {
            this.len = 0;
            return;
        }
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        int st = 0;
        Node[] stack = new Node[indices.length * 2];
        stack[st++] = new Node(LEAF, false, 0, indices.length, 0);
        int nodeLen = 0;
        while (st != 0) {
            ensureNode(nodeLen);
            final Node n = stack[--st];
            final int curr = nodeLen++;
            if (n.len() <= 1) {
                final int p = indices[n.offset()];
                nrl[curr] = LEAF;
                nxl[curr] = coords[0][p];
                nyl[curr] = coords[1][p];
                nil[curr] = p;
            } else {
                final int axis = (n.depth()) % 2;
                final int med = (n.len() - 1) / 2;
                final int k = n.offset() + med;
                final double[] coord = coords[axis];
                PartialSort.nthElement(indices, coord, n.offset(), n.offset() + n.len() - 1, k);

                nxl[curr] = coord[indices[k]];
                stack[st++] = new Node(curr, false, n.offset() + med + 1, n.len() - med - 1, n.depth() + 1);
                stack[st++] = new Node(curr, true, n.offset(), med + 1, n.depth() + 1);
            }
            if (n.parent() != LEAF) {
                nrl[n.parent()] = n.left() ? LEAF : curr;
            }
        }
        arr = it.unimi.dsi.fastutil.ints.IntArrays.ensureCapacity(arr, len = nodeLen, 0);
    }

    private void ensureNode(final int preserve) {
        int length = preserve + 1;
        if (length < nrl.length) {
            return;
        }
        length += length >> 1;
        if (length < INITIAL_CAPACITY) {
            length = INITIAL_CAPACITY;
        }
        nrl = it.unimi.dsi.fastutil.ints.IntArrays.forceCapacity(nrl, length, preserve);
        nxl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nxl, length, preserve);
        nyl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nyl, length, preserve);
        nil = it.unimi.dsi.fastutil.ints.IntArrays.forceCapacity(nil, length, preserve);
    }

    private static double euclideanDistanceSquared(double dx, double dy) {
        return ENABLE_FMA
            ? Math.fma(dy, dy, dx * dx)
            : dx * dx + dy * dy;
    }

    public int len() {
        return this.len;
    }

    public boolean isEmpty() {
        return this.len == 0;
    }

    public double nearestSqr(final double tx, final double ty, double dist, final int[] stack) {
        if (this.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        final int[] right = this.nrl;
        final double[] split = this.nxl;
        final double[] y = this.nyl;
        int i = 0, j = 0, a = 0;
        while (true) {
            // descent near
            while (true) {
                final int r = right[i];
                if (r == LEAF) {
                    dist = Math.min(dist, euclideanDistanceSquared(split[i] - tx, y[i] - ty));
                    break;
                }
                final double d = (a == 0 ? tx : ty) - split[i];
                if (d * d < dist) {
                    stack[j++] = i | (a << BITS);
                }
                i = d < 0.0 ? i + 1 : r;
                a = a == 1 ? 0 : 1;
            }
            // backtrack far
            while (true) {
                if (j == 0) {
                    return dist;
                }
                j--;
                final int s = stack[j];
                final int n = s & MASK;
                final int axis = s >>> BITS;
                final double d = (axis == 0 ? tx : ty) - split[n];
                if (d * d < dist) {
                    i = d < 0.0 ? right[n] : n + 1;
                    a = axis == 1 ? 0 : 1;
                    break;
                }
            }
        }
    }

    private record Node(int parent, boolean left, int offset, int len, int depth) {
    }
}
