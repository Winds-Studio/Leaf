package org.dreeam.leaf.util;

@org.jspecify.annotations.NullMarked
public final class KDTree3D {

    private static final boolean FMA = Boolean.getBoolean("Leaf.enableFMA");

    private static final double[] EMPTY_DOUBLES = {};
    private static final int[] EMPTY_INTS = {};
    private static final Node[] EMPTY_NODES = {};

    private static final int INITIAL_CAPACITY = 8;

    /// indicate empty on [#search]
    /// indicate leaf node on [#nrl]
    private static final int SENTINEL = -1;
    private Node[] stack = EMPTY_NODES;
    private int[] search = EMPTY_INTS;
    /// Right node index for internal or [#SENTINEL] for leaf
    private int[] nrl = EMPTY_INTS;
    // split for internal or x coordinate for leaf
    private double[] nxl = EMPTY_DOUBLES;
    // y coordinate for leaf
    private double[] nyl = EMPTY_DOUBLES;
    // z coordinate for leaf
    private double[] nzl = EMPTY_DOUBLES;
    // index for leaf
    private int[] nil = EMPTY_INTS;

    public void build(final double[][] coords, final int[] indices) {
        if (indices.length == 0 || coords.length != 3) {
            ensureSearch(0, 0);
            return;
        }

        int st = 0;
        ensureConstruct(st);
        stack[st++] = new Node(SENTINEL, false, 0, indices.length, 0);
        int nodeLen = 0;
        while (st != 0) {
            ensureNode(nodeLen);
            final Node n = stack[--st];
            final int curr = nodeLen++;
            if (n.len() <= 1) {
                final int p = indices[n.offset()];
                nrl[curr] = SENTINEL;
                nxl[curr] = coords[0][p];
                nyl[curr] = coords[1][p];
                nzl[curr] = coords[2][p];
                nil[curr] = p;
            } else {
                final int axis = (n.depth()) % 3;
                final int med = (n.len() - 1) / 2;
                final int k = n.offset() + med;
                final double[] coord = coords[axis];
                PartialSort.nthElement(indices, coord, n.offset(), n.offset() + n.len() - 1, k);

                nxl[curr] = coord[indices[k]];

                ensureConstruct(st);
                stack[st++] = new Node(curr, false, n.offset() + med + 1, n.len() - med - 1, n.depth() + 1);
                stack[st++] = new Node(curr, true, n.offset(), med + 1, n.depth() + 1);
            }
            if (n.parent() != SENTINEL) {
                nrl[n.parent()] = n.left() ? SENTINEL : curr;
            }
        }

        ensureSearch(indices.length, nodeLen);
    }

    private void ensureSearch(final int length, final int nodeLen) {
        if (search.length < nodeLen + 8) {
            search = new int[nodeLen + 8];
        }
        search[0] = length == 0 ? SENTINEL : 0;
    }

    private void ensureConstruct(final int st) {
        if (st != stack.length && st + 1 != stack.length) {
            return;
        }
        final int newLen = stack.length + 2;
        final Node[] b = new Node[Math.max(INITIAL_CAPACITY, newLen + (newLen >> 1))];
        System.arraycopy(stack, 0, b, 0, st);
        stack = b;
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
        nzl = it.unimi.dsi.fastutil.doubles.DoubleArrays.forceCapacity(nzl, length, preserve);
        nil = it.unimi.dsi.fastutil.ints.IntArrays.forceCapacity(nil, length, preserve);
    }

    public double nearestSqr(final double tx, final double ty, final double tz, double dist) {
        final int[] stack = this.search;
        final int[] nrl = this.nrl;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        if (stack.length == 0 || stack[0] == SENTINEL) {
            return Double.POSITIVE_INFINITY;
        }
        int i = 0, j = 0, curr = 0;
        while (true) {
            final int right = nrl[j];
            if (right == SENTINEL) {
                final double dx = nxl[j] - tx;
                final double dy = nyl[j] - ty;
                final double dz = nzl[j] - tz;
                dist = Math.min(dist, FMA
                    ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx))
                    : dx * dx + dy * dy + dz * dz);
                break;
            } else {
                final int next = ((curr + 1) % 3) << 30;
                final int left = j + 1;
                final double delta = (curr == 0 ? tx : curr == 1 ? ty : tz) - nxl[j];
                final boolean push = delta * delta < dist;
                if (delta < 0.0) {
                    if (push) {
                        stack[i++] = right | next;
                    }
                    j = left;
                } else {
                    if (push) {
                        stack[i++] = left | next;
                    }
                    j = right;
                }
                curr = ((curr + 1) % 3);
            }
        }
        while (i != 0) {
            j = stack[--i];
            final int k = j & 0x3FFF_FFFF;
            final int right = nrl[k];
            if (right == SENTINEL) {
                final double dx = nxl[k] - tx;
                final double dy = nyl[k] - ty;
                final double dz = nzl[k] - tz;
                dist = Math.min(dist, FMA
                    ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx))
                    : dx * dx + dy * dy + dz * dz);
            } else {
                final int axis = j >>> 30;
                final int next = ((axis + 1) % 3) << 30;
                final int left = (k + 1) | next;
                final double delta = (axis == 0 ? tx : axis == 1 ? ty : tz) - nxl[k];
                final boolean push = delta * delta < dist;
                if (delta < 0.0) {
                    // near = left, far = right, left first
                    if (push) {
                        stack[i++] = right | next;
                    }
                    stack[i++] = left;
                } else {
                    // near = right, far = left, right first
                    if (push) {
                        stack[i++] = left;
                    }
                    stack[i++] = right | next;
                }
            }
        }
        return dist;
    }

    public int nearestIdx(final double tx, final double ty, final double tz, double dist) {
        final int[] stack = this.search;
        final int[] nrl = this.nrl;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        if (stack.length == 0 || stack[0] == SENTINEL) {
            return -1;
        }
        int i = 0, j = 0, curr = 0, nearest = -1;
        while (true) {
            final int right = nrl[j];
            if (right == SENTINEL) {
                final double dx = nxl[j] - tx;
                final double dy = nyl[j] - ty;
                final double dz = nzl[j] - tz;
                final double candidate = FMA
                    ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx))
                    : dx * dx + dy * dy + dz * dz;
                if (candidate < dist) {
                    dist = candidate;
                    nearest = nil[j];
                }
                break;
            } else {
                final int next = ((curr + 1) % 3) << 30;
                final int left = j + 1;
                final double delta = (curr == 0 ? tx : curr == 1 ? ty : tz) - nxl[j];
                final boolean push = delta * delta < dist;
                if (delta < 0.0) {
                    if (push) {
                        stack[i++] = right | next;
                    }
                    j = left;
                } else {
                    if (push) {
                        stack[i++] = left | next;
                    }
                    j = right;
                }
                curr = ((curr + 1) % 3);
            }
        }
        while (i != 0) {
            j = stack[--i];
            final int k = j & 0x3FFF_FFFF;
            final int right = nrl[k];
            if (right == SENTINEL) {
                final double dx = nxl[k] - tx;
                final double dy = nyl[k] - ty;
                final double dz = nzl[k] - tz;
                final double candidate = FMA
                    ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx))
                    : dx * dx + dy * dy + dz * dz;
                if (candidate < dist) {
                    dist = candidate;
                    nearest = nil[k];
                }
            } else {
                final int axis = j >>> 30;
                final int next = ((axis + 1) % 3) << 30;
                final int left = (k + 1) | next;
                final double delta = (axis == 0 ? tx : axis == 1 ? ty : tz) - nxl[k];
                final boolean push = delta * delta < dist;
                if (delta < 0.0) {
                    // near = left, far = right, left first
                    if (push) {
                        stack[i++] = right | next;
                    }
                    stack[i++] = left;
                } else {
                    // near = right, far = left, right first
                    if (push) {
                        stack[i++] = left;
                    }
                    stack[i++] = right | next;
                }
            }
        }
        return nearest;
    }

    private record Node(int parent, boolean left, int offset, int len, int depth) {
    }
}
