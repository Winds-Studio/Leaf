package org.dreeam.leaf.world;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.DespawnRange;
import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.LeafBootstrap;

public final class DespawnMap {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final int[] EMPTY_INTS = {};
    private static final boolean[] EMPTY_BOOLS = {};
    private static final boolean FMA = LeafBootstrap.enableFMA;
    private static final int LEAF_THRESHOLD = 4;
    private static final int INITIAL_DEQUE_CAP = 16;
    private static final int INITIAL_NODE_CAP = 16;
    private static final int ROOT = -1;

    /// Stack for tree construction
    private final Deque stack = new Deque();
    /// Stack for tree traversal during nearest neighbor search
    private final IntDeque search = new IntDeque();

    private ServerPlayer[] pl = EMPTY_PLAYERS;
    private double[] pxl = EMPTY_DOUBLES;
    private double[] pyl = EMPTY_DOUBLES;
    private double[] pzl = EMPTY_DOUBLES;
    /// Node length
    private int nl = 0;

    /// Node X coordinates for each internal node
    private double[] nxl = EMPTY_DOUBLES;
    /// Node Y coordinates for each internal node
    private double[] nyl = EMPTY_DOUBLES;
    /// Node Z coordinates for each internal node
    private double[] nzl = EMPTY_DOUBLES;
    /// Left child indices for each internal node
    private int[] lnl = EMPTY_INTS;
    /// Right child indices for each internal node
    private int[] rnl = EMPTY_INTS;
    /// Split axis for each internal node
    private int[] axl = EMPTY_INTS;
    /// indicating leaf node
    private boolean[] leaf = EMPTY_BOOLS;
    /// Nested player indices of leaf nodes
    private final IntArrayList nbl = new IntArrayList();
    /// Offsets for each player list of leaf node
    private int[] nbi = EMPTY_INTS;
    /// Lengths for each player list of leaf node
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
        final MapDouble mx = new MapDouble(pxl);
        final MapDouble my = new MapDouble(pyl);
        final MapDouble mz = new MapDouble(pzl);
        final int[] data = new int[pls];
        for (int i = 0; i < pls; i++) {
            data[i] = i;
        }
        stack.push(new Node(-1, false, 0, pls, 0));
        while (!stack.isEmpty()) {
            final Node n = stack.poll();
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            grow(nl + 1);
            if (len <= LEAF_THRESHOLD) {
                nbi[nl] = nbl.size();
                nbs[nl] = len;
                IntArrays.quickSort(data, offset, offset + len);
                nbl.addElements(nbl.size(), data, offset, len);
                leaf[nl] = true;
            } else {
                final int axis = depth % 3;
                IntArrays.quickSort(data, offset, offset + len, axis == 0 ? mx : axis == 1 ? my : mz);

                final int median = len / 2;
                final int pivot = data[offset + median];

                nbs[nl] = 0;
                nbi[nl] = 0;
                nxl[nl] = pxl[pivot];
                nyl[nl] = pyl[pivot];
                nzl[nl] = pzl[pivot];
                axl[nl] = axis;
                leaf[nl] = false;
                stack.push(new Node(nl, true, offset, median, depth + 1));
                stack.push(new Node(nl, false, offset + median + 1, len - median - 1, depth + 1));
            }
            lnl[nl] = ROOT;
            rnl[nl] = ROOT;
            if (n.parent >= 0) {
                if (n.left) {
                    lnl[n.parent] = nl;
                } else {
                    rnl[n.parent] = nl;
                }
            }
            nl++;
        }
        stack.clear();
    }

    private void reset() {
        pl = EMPTY_PLAYERS;
        nl = 0;
        nbl.clear();
    }

    private void grow(int capacity) {
        if (capacity <= nxl.length) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_NODE_CAP) {
            capacity = INITIAL_NODE_CAP;
        }
        nxl = DoubleArrays.forceCapacity(nxl, capacity, nl);
        nyl = DoubleArrays.forceCapacity(nyl, capacity, nl);
        nzl = DoubleArrays.forceCapacity(nzl, capacity, nl);
        lnl = IntArrays.forceCapacity(lnl, capacity, nl);
        rnl = IntArrays.forceCapacity(rnl, capacity, nl);
        axl = IntArrays.forceCapacity(axl, capacity, nl);
        leaf = BooleanArrays.forceCapacity(leaf, capacity, nl);
        nbi = IntArrays.forceCapacity(nbi, capacity, nl);
        nbs = IntArrays.forceCapacity(nbs, capacity, nl);
    }

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }

    private ServerPlayer nearest(final double tx, final double ty, final double tz) {
        if (nl == 0) {
            return null;
        }

        int nearest = -1;
        double dist = Double.POSITIVE_INFINITY;
        final double[] pxl = this.pxl;
        final double[] pyl = this.pyl;
        final double[] pzl = this.pzl;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        final int[] axl = this.axl;
        final int[] lnl = this.lnl;
        final int[] rnl = this.rnl;
        final boolean[] leaf = this.leaf;
        final int[] nbl = this.nbl.elements();
        final int[] nbi = this.nbi;
        final int[] nbs = this.nbs;
        search.push(0);
        while (!search.isEmpty()) {
            final int idx = search.poll();
            if (leaf[idx]) {
                int bucket = nbi[idx];
                final int end = bucket + nbs[idx];
                for (; bucket < end; bucket++) {
                    final int p = nbl[bucket];
                    final double dx = pxl[p] - tx;
                    final double dy = pyl[p] - ty;
                    final double dz = pzl[p] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    if (d2 < dist) {
                        dist = d2;
                        nearest = p;
                    }
                }
            } else {
                final int axis = axl[idx];
                final double delta = axis == 0 ? tx - nxl[idx] : axis == 1 ? ty - nyl[idx] : tz - nzl[idx];
                final int nearIdx;
                final int farIdx;
                if (delta < 0.0) {
                    nearIdx = lnl[idx];
                    farIdx = rnl[idx];
                } else {
                    nearIdx = rnl[idx];
                    farIdx = lnl[idx];
                }
                if (nearIdx != -1) {
                    search.push(nearIdx);
                }
                if (farIdx != -1 && delta * delta < dist) {
                    search.push(farIdx);
                }
            }
        }
        search.clear();
        return nearest != -1 ? this.pl[nearest] : null;
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

        private Node poll() {
            final Node t = array[start];
            if (++start == length) start = 0;
            // no resize
            return t;
        }

        private void push(final Node node) {
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

    /// @see it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
    private final static class IntDeque {
        private int[] array;
        private int length;
        private int start;
        private int end;

        private IntDeque() {
            array = new int[INITIAL_DEQUE_CAP];
            length = array.length;
        }

        private void clear() {
            start = 0;
            end = 0;
        }

        private boolean isEmpty() {
            return end == start;
        }

        private int poll() {
            final int t = array[start];
            if (++start == length) start = 0;
            // no resize
            return t;
        }

        private void push(final int node) {
            array[end++] = node;
            if (end == length) end = 0;
            if (end == start) resize(length, 2 * length);
        }

        private void resize(final int size, final int newLength) {
            final int[] newArray = new int[newLength];
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
        public int compare(int k1, int k2) {
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
        ServerPlayer nearestPlayer = nearest(x, y, z);
        if (nearestPlayer == null) {
            return;
        }
        Level world = mob.level();
        final WorldConfiguration.Entities.Spawning.DespawnRangePair despawnRangePair = world.paperConfig().entities.spawning.despawnRanges.get(mob.getType().getCategory());
        final DespawnRange.Shape shape = world.paperConfig().entities.spawning.despawnRangeShape;
        final double dy = nearestPlayer.getY() - y;
        final double dyAbs = Math.abs(dy);
        final double dxSqr = Mth.square(nearestPlayer.getX() - x);
        final double dySqr = Mth.square(dy);
        final double dzSqr = Mth.square(nearestPlayer.getZ() - z);
        final double distanceSqr = dxSqr + dzSqr + dySqr;
        if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dyAbs) && mob.removeWhenFarAway(distanceSqr)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dyAbs)) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(distanceSqr)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }
}
