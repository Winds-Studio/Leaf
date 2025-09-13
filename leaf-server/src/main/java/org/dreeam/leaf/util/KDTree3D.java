package org.dreeam.leaf.util;

@org.jspecify.annotations.NullMarked
public final class KDTree3D {

    private static final boolean FMA = Boolean.getBoolean("Leaf.enableFMA");

    private static final double[] EMPTY_DOUBLES = {};
    private static final int[] EMPTY_INTS = {};
    private static final long[] EMPTY_LONGS = {};
    private static final Node[] EMPTY_NODES = {};

    private static final long AXIS_X = 0L;
    private static final long AXIS_Y = 1L;
    private static final long AXIS_Z = 2L;

    private static final int INITIAL_CAPACITY = 8;

    /// indicate empty on [#search]
    private static final int SENTINEL = -1;
    /// indicate leaf node on [#nll]
    private static final long NIL = -1L;

    private static final long AXIS_MASK = 0b11L;
    private static final int LEFT_CHILD_OFFSET = 2;
    private static final long LEFT_MASK = 0xffff_fffcL;
    private static final long RIGHT_MASK = 0x3fff_ffff_0000_0000L;
    private static final int RIGHT_CHILD_OFFSET = LEFT_CHILD_OFFSET + 30;

    private Node[] stack = EMPTY_NODES;
    private int[] search = EMPTY_INTS;
    /// Right(30) Left(30) Axis(2) for each internal node
    private long[] nll = EMPTY_LONGS;
    private double[] nxl = EMPTY_DOUBLES;
    private double[] nyl = EMPTY_DOUBLES;
    private double[] nzl = EMPTY_DOUBLES;
    private int[] nil = EMPTY_INTS;

    public void build(final double[][] coords, final int[] indices) {
        if (indices.length == 0) {
            ensureSearch(0, 0);
            return;
        }

        int st = 0;
        growCon(st);
        stack[st++] = new Node(SENTINEL, false, 0, indices.length, 0);
        int nodeLen = 0;
        while (st != 0) {
            growNode(nodeLen);
            final Node n = stack[--st];
            final int curr = nodeLen++;
            if (n.len() <= 1) {
                nll[curr] = NIL;
                final int p = indices[n.offset()];
                nxl[curr] = coords[(int) AXIS_X][p];
                nyl[curr] = coords[(int) AXIS_Y][p];
                nzl[curr] = coords[(int) AXIS_Z][p];
                nil[curr] = p;
            } else {
                final int axis = axisOrder(n.depth());
                final int med = (n.len() - 1) / 2;
                final int k = n.offset() + med;
                final double[] coord = coords[axis];
                PartialSort.nthElement(indices, coord, n.offset(), n.offset() + n.len() - 1, k);

                nll[curr] = RIGHT_MASK | LEFT_MASK | axis;
                nxl[curr] = coord[indices[k]];

                growCon(st);
                stack[st++] = new Node(curr, false, n.offset() + med + 1, n.len() - med - 1, n.depth() + 1);
                stack[st++] = new Node(curr, true, n.offset(), med + 1, n.depth() + 1);
            }
            setChild(n.parent(), n.left(), curr);
        }

        ensureSearch(indices.length, nodeLen);
    }

    private void setChild(final int parent, final boolean left, final long curr) {
        if (parent == SENTINEL) {
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

    private static int axisOrder(final int depth) {
        final int r = depth % 3;
        return r == 0 ? (int) AXIS_X : r == 1 ? (int) AXIS_Z : (int) AXIS_Y;
    }

    private void ensureSearch(final int length, final int nodeLen) {
        if (search.length < Math.max(64, nodeLen * 2)) {
            search = new int[Math.max(64, nodeLen * 2)];
        }
        search[0] = length == 0 ? SENTINEL : 0;
    }

    private void growCon(final int st) {
        if (st != stack.length && st + 1 != stack.length) {
            return;
        }
        final int newLen = stack.length + 2;
        final Node[] b = new Node[Math.max(INITIAL_CAPACITY, newLen + (newLen >> 1))];
        System.arraycopy(stack, 0, b, 0, st);
        stack = b;
    }

    private void growNode(final int preserve) {
        int length = preserve + 1;
        if (length < nll.length) {
            return;
        }
        length += length >> 1;
        if (length < INITIAL_CAPACITY) {
            length = INITIAL_CAPACITY;
        }
        nll = it.unimi.dsi.fastutil.longs.LongArrays.forceCapacity(nll, length, preserve);
        nxl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nxl, length, preserve);
        nyl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nyl, length, preserve);
        nzl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nzl, length, preserve);
        nil = it.unimi.dsi.fastutil.ints.IntArrays.forceCapacity(nil, length, preserve);
    }

    public double nearestSqr(final double tx, final double ty, final double tz, double dist) {
        final int[] stack = this.search;
        final long[] nll = this.nll;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        if (stack.length == 0 || stack[0] == SENTINEL) {
            return Double.POSITIVE_INFINITY;
        }
        stack[0] = 0;
        int i = 1;
        while (i != 0) {
            final int j = stack[--i];
            final long data = nll[j];
            if (data == NIL) {
                final double dx = nxl[j] - tx;
                final double dy = nyl[j] - ty;
                final double dz = nzl[j] - tz;
                dist = Math.min(dist, FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz);
            } else {
                final boolean hasLeft = (data & LEFT_MASK) != LEFT_MASK;
                final boolean hasRight = (data & RIGHT_MASK) != RIGHT_MASK;
                final int left = (int) ((data & LEFT_MASK) >>> LEFT_CHILD_OFFSET);
                final int right = (int) (data >>> RIGHT_CHILD_OFFSET);
                final long axis = data & AXIS_MASK;
                final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nxl[j];
                if (delta < 0.0) {
                    if (hasRight && delta * delta < dist) {
                        stack[i++] = right;
                    }
                    if (hasLeft) {
                        stack[i++] = left;
                    }
                } else {
                    if (hasLeft && delta * delta < dist) {
                        stack[i++] = left;
                    }
                    if (hasRight) {
                        stack[i++] = right;
                    }
                }
            }
        }
        return dist;
    }

    public int nearest(final double tx, final double ty, final double tz, double dist) {
        final int[] stack = this.search;
        final long[] nll = this.nll;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        if (stack.length == 0 || stack[0] == SENTINEL) {
            return -1;
        }
        stack[0] = 0;
        int i = 1;
        int nearest = -1;
        while (i != 0) {
            final int j = stack[--i];
            final long data = nll[j];
            if (data == NIL) {
                final double dx = nxl[j] - tx;
                final double dy = nyl[j] - ty;
                final double dz = nzl[j] - tz;
                final double candidate = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                if (candidate < dist) {
                    dist = candidate;
                    nearest = nil[j];
                }
            } else {
                final boolean hasLeft = (data & LEFT_MASK) != LEFT_MASK;
                final boolean hasRight = (data & RIGHT_MASK) != RIGHT_MASK;
                final int left = (int) ((data & LEFT_MASK) >>> LEFT_CHILD_OFFSET);
                final int right = (int) (data >>> RIGHT_CHILD_OFFSET);
                final long axis = data & AXIS_MASK;
                final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nxl[j];
                if (delta < 0.0) {
                    if (hasRight && delta * delta < dist) {
                        stack[i++] = right;
                    }
                    if (hasLeft) {
                        stack[i++] = left;
                    }
                } else {
                    if (hasLeft && delta * delta < dist) {
                        stack[i++] = left;
                    }
                    if (hasRight) {
                        stack[i++] = right;
                    }
                }
            }
        }
        return nearest;
    }

    private record Node(int parent, boolean left, int offset, int len, int depth) {
    }
}
