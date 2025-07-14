package org.dreeam.leaf.world;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.LeafBootstrap;
import org.dreeam.leaf.config.modules.opt.OptimizeDespawn;
import org.dreeam.leaf.util.queue.IntDeque;

public final class DespawnMap {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final int[] EMPTY_INTS = {};
    private static final boolean[] EMPTY_BOOLEANS = {};
    static final boolean FMA = LeafBootstrap.enableFMA;
    private static final boolean SIMD = SIMDDetection.isEnabled();
    private static final int LEAF_THRESHOLD = SIMD ? DespawnVectorAPI.DOUBLE_VECTOR_LENGTH : 4;
    private static final int INITIAL_DEQUE_CAP = 16;
    private static final int INITIAL_NODE_CAP = 8;
    static final int ROOT = -1;
    static final int AXIS_X = 0;
    static final int AXIS_Y = 1;

    /// FIFO Queue for tree construction
    private final Deque stack = new Deque();
    /// FIFO Queue for tree traversal during nearest neighbor search
    private final IntDeque search = new IntDeque(INITIAL_DEQUE_CAP);

    private ServerPlayer[] pl = EMPTY_PLAYERS;
    private double[] pxl = EMPTY_DOUBLES;
    private double[] pyl = EMPTY_DOUBLES;
    private double[] pzl = EMPTY_DOUBLES;
    /// Node length
    private int nl = 0;
    /// Bucket length
    private int bl = 0;

    /// Node X coordinates for each internal node
    private double[] nxl = EMPTY_DOUBLES;
    /// Node Y coordinates for each internal node
    private double[] nyl = EMPTY_DOUBLES;
    /// Node Z coordinates for each internal node
    private double[] nzl = EMPTY_DOUBLES;
    /// Left child indices for each internal node
    private int[] nll = EMPTY_INTS;
    /// Right child indices for each internal node
    private int[] nrl = EMPTY_INTS;
    /// Split axis for each internal node
    private int[] axl = EMPTY_INTS;
    /// indicating leaf node
    private boolean[] leaf = EMPTY_BOOLEANS;
    /// Nested player X coordinates of leaf nodes
    private double[] bxl = EMPTY_DOUBLES;
    /// Nested player Y coordinates of leaf nodes
    private double[] byl = EMPTY_DOUBLES;
    /// Nested player Z coordinates of leaf nodes
    private double[] bzl = EMPTY_DOUBLES;
    /// Offsets for each player list of leaf nodes
    private int[] nbi = EMPTY_INTS;
    /// Lengths for each player list of leaf nodes
    private int[] nbs = EMPTY_INTS;
    public boolean enabled = false;

    private void build(ServerLevel world) {
        final ServerPlayer[] playerArr = world.players().toArray(EMPTY_PLAYERS);
        final ServerPlayer[] list = new ServerPlayer[playerArr.length];
        int newSize = 0;
        for (ServerPlayer player1 : playerArr) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player1)) {
                list[newSize++] = player1;
            }
        }
        this.pl = ObjectArrays.setLength(list, newSize);

        final int pls = this.pl.length;
        if (pls != this.pxl.length) {
            this.pxl = new double[pls];
            this.pyl = new double[pls];
            this.pzl = new double[pls];
        }
        for (int i = 0; i < pls; i++) {
            this.pxl[i] = this.pl[i].getX();
            this.pyl[i] = this.pl[i].getY();
            this.pzl[i] = this.pl[i].getZ();
        }
        final MapDouble[] ml = new MapDouble[]{new MapDouble(pxl), new MapDouble(pyl), new MapDouble(pzl)};
        final int[] data = new int[pls];
        for (int i = 0; i < pls; i++) {
            data[i] = i;
        }
        stack.enqueueBack(new Node(ROOT, false, 0, pls, 0));
        while (!stack.isEmpty()) {
            final Node n = stack.dequeueFront();
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            grow(nl + 1);
            if (len <= LEAF_THRESHOLD) {
                nbi[nl] = bl;
                nbs[nl] = len;
                growBucket(bl + len);
                for (int i = 0; i < len; i++) {
                    int p = data[offset + i];
                    bxl[bl + i] = pxl[p];
                    byl[bl + i] = pyl[p];
                    bzl[bl + i] = pzl[p];
                }
                bl += len;
                leaf[nl] = true;
            } else {
                final int axis = depth % 3;
                IntArrays.quickSort(data, offset, offset + len, ml[axis]);

                final int median = len / 2;
                final int pivot = data[offset + median];

                nbs[nl] = 0;
                nbi[nl] = 0;
                nxl[nl] = pxl[pivot];
                nyl[nl] = pyl[pivot];
                nzl[nl] = pzl[pivot];
                axl[nl] = axis;
                leaf[nl] = false;
                stack.enqueueBack(new Node(nl, true, offset, median, depth + 1));
                stack.enqueueBack(new Node(nl, false, offset + median + 1, len - median - 1, depth + 1));
            }
            nll[nl] = ROOT;
            nrl[nl] = ROOT;
            if (n.parent >= 0) {
                if (n.left) {
                    nll[n.parent] = nl;
                } else {
                    nrl[n.parent] = nl;
                }
            }
            nl++;
        }
        stack.clear();
    }

    private void reset() {
        pl = EMPTY_PLAYERS;
        nl = 0;
        bl = 0;
    }

    private void grow(int capacity) {
        if (capacity <= nl) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_NODE_CAP) {
            capacity = INITIAL_NODE_CAP;
        }
        nxl = DoubleArrays.forceCapacity(nxl, capacity, nl);
        nyl = DoubleArrays.forceCapacity(nyl, capacity, nl);
        nzl = DoubleArrays.forceCapacity(nzl, capacity, nl);
        nll = IntArrays.forceCapacity(nll, capacity, nl);
        nrl = IntArrays.forceCapacity(nrl, capacity, nl);
        axl = IntArrays.forceCapacity(axl, capacity, nl);
        leaf = BooleanArrays.forceCapacity(leaf, capacity, nl);
        nbi = IntArrays.forceCapacity(nbi, capacity, nl);
        nbs = IntArrays.forceCapacity(nbs, capacity, nl);
    }

    private void growBucket(int capacity) {
        if (capacity <= bl) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_NODE_CAP) {
            capacity = INITIAL_NODE_CAP;
        }
        bxl = DoubleArrays.forceCapacity(bxl, capacity, bl);
        byl = DoubleArrays.forceCapacity(byl, capacity, bl);
        bzl = DoubleArrays.forceCapacity(bzl, capacity, bl);
    }

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }

    private double nearest(final double tx, final double ty, final double tz) {
        if (nl == 0) {
            return Double.POSITIVE_INFINITY;
        }

        double dist = Double.POSITIVE_INFINITY;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        final int[] axl = this.axl;
        final int[] nll = this.nll;
        final int[] nrl = this.nrl;
        final boolean[] leaf = this.leaf;
        final double[] bxl = this.bxl;
        final double[] byl = this.byl;
        final double[] bzl = this.bzl;
        final int[] nbi = this.nbi;
        final int[] nbs = this.nbs;
        search.enqueueBack(0);
        if (SIMD) {
            dist = DespawnVectorAPI.nearest(search, nxl, nyl, nzl, axl, nll, nrl, leaf, bxl, byl, bzl, nbi, nbs, tx, ty, tz);
        } else {
            while (!search.isEmpty()) {
                final int idx = search.dequeueFront();
                if (leaf[idx]) {
                    int bucket = nbi[idx];
                    final int end = bucket + nbs[idx];
                    for (; bucket < end; bucket++) {
                        final double dx = bxl[bucket] - tx;
                        final double dy = byl[bucket] - ty;
                        final double dz = bzl[bucket] - tz;
                        final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                        if (d2 < dist) {
                            dist = d2;
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
        }
        search.clear();
        return dist;
    }

    /// @see it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue
    private final static class Deque {
        private Node[] array;
        private int length;
        private int start;
        private int end;

        private Deque() {
            array = new Node[INITIAL_DEQUE_CAP];
            length = array.length;
        }

        private void clear() {
            start = 0;
            end = 0;
        }

        private boolean isEmpty() {
            return end == start;
        }

        private Node dequeueFront() {
            final Node t = array[start];
            if (++start == length) start = 0;
            // no resize
            return t;
        }

        private void enqueueBack(final Node node) {
            array[end++] = node;
            if (end == length) end = 0;
            if (end == start) resize(length, 2 * length);
        }

        private void resize(final int size, final int newLength) {
            final Node[] newArray = new Node[newLength];
            assert end == start;
            if (size != 0) {
                System.arraycopy(array, start, newArray, 0, length - start);
                System.arraycopy(array, 0, newArray, length - start, end);
            }
            start = 0;
            end = size;
            array = newArray;
            length = newLength;
        }
    }

    private record MapDouble(double[] a) implements IntComparator {
        @Override
        public int compare(final int k1, final int k2) {
            return Double.compare(a[k1], a[k2]);
        }
    }

    public void tick(ServerLevel world, EntityTickList entityTickList) {
        build(world);
        enabled = true;
        entityTickList.forEach(entity -> {
            if (!entity.isRemoved()) {
                entity.checkDespawn();
            }
        });
        enabled = false;
        reset();
    }

    public void checkDespawn(Mob mob) {
        final double x = mob.getX();
        final double y = mob.getY();
        final double z = mob.getZ();
        final double dist = nearest(x, y, z);
        if (dist == Double.POSITIVE_INFINITY) {
            return;
        }

        final int i = mob.getType().getCategory().ordinal();
        if (dist > OptimizeDespawn.hard[i] && mob.removeWhenFarAway(dist)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (dist > OptimizeDespawn.sort[i]) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(dist)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }
}
