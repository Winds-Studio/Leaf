package org.dreeam.leaf.world;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import io.papermc.paper.configuration.WorldConfiguration;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.Map;
import java.util.OptionalInt;

public final class DespawnMap {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final long[] EMPTY_LONGS = {};
    private static final int[] EMPTY_INTS = {};
    static final boolean FMA = Boolean.getBoolean("Leaf.enableFMA");
    private static final boolean SIMD = SIMDDetection.isEnabled();
    private static final int LEAF_THRESHOLD = SIMD ? DespawnVectorAPI.DOUBLE_VECTOR_LENGTH : 4;
    private static final int INITIAL_CAP = 8;
    private static final int INSERTION_SORT = 16;
    static final long LEAF = -1L;
    static final long AXIS_X = 0L;
    static final long AXIS_Y = 1L;
    static final long AXIS_Z = 2L;
    static final long LEFT_MASK = 0xfffffffcL;
    static final long RIGHT_MASK = 0x3fffffff00000000L;
    static final long AXIS_MASK = 0b11L;
    static final long SIGN_BIT = 0x8000_0000_0000_0000L;

    /// Stack for tree construction
    private final Stack stack = new Stack(INITIAL_CAP);
    /// Stack for tree traversal
    private int[] search = EMPTY_INTS;

    private int nodeLen = 0;
    private int bucketLen = 0;

    /// Node coordinate for each internal node
    private double[] nsl = EMPTY_DOUBLES;
    /// Offsets(32) Lengths(32) for each player list of leaf nodes
    private long[] nbl = EMPTY_LONGS;
    /// Left(30) Right(30) Axis(2) for each internal node
    private long[] nll = EMPTY_LONGS;
    /// Nested player X coordinates of leaf nodes
    private double[] bxl = EMPTY_DOUBLES;
    /// Nested player Y coordinates of leaf nodes
    private double[] byl = EMPTY_DOUBLES;
    /// Nested player Z coordinates of leaf nodes
    private double[] bzl = EMPTY_DOUBLES;

    private final double[] hard;
    private final double[] sort;

    public DespawnMap(WorldConfiguration worldConfiguration) {
        MobCategory[] caps = MobCategory.values();
        hard = new double[caps.length];
        sort = new double[caps.length];
        for (int i = 0; i < caps.length; i++) {
            sort[i] = caps[i].getNoDespawnDistance();
            hard[i] = caps[i].getDespawnDistance();
        }
        for (Map.Entry<MobCategory, WorldConfiguration.Entities.Spawning.DespawnRangePair> e : worldConfiguration.entities.spawning.despawnRanges.entrySet()) {
            OptionalInt a = e.getValue().soft().verticalLimit.value();
            OptionalInt b = e.getValue().soft().horizontalLimit.value();
            OptionalInt c = e.getValue().hard().verticalLimit.value();
            OptionalInt d = e.getValue().hard().horizontalLimit.value();
            if (a.isPresent() && b.isPresent() && a.getAsInt() == b.getAsInt()) {
                sort[e.getKey().ordinal()] = a.getAsInt();
            }
            if (c.isPresent() && d.isPresent() && c.getAsInt() == d.getAsInt()) {
                hard[e.getKey().ordinal()] = c.getAsInt();
            }
        }
        for (int i = 0; i < caps.length; i++) {
            if (sort[i] > 0.0) {
                sort[i] = sort[i] * sort[i];
            }
            if (hard[i] > 0.0) {
                hard[i] = hard[i] * hard[i];
            }
        }
    }

    private void build(double[] coordX, double[] coordY, double[] coordZ) {
        final double[][] map = {coordX, coordY, coordZ};
        final int[] data = new int[coordX.length];
        for (int i = 0; i < coordX.length; i++) {
            data[i] = i;
        }
        stack.push(new Node(-1, false, 0, data.length, 0));
        while (!stack.isEmpty()) {
            grow();

            final Node n = stack.pop();
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            final int curr = nodeLen++;
            if (len <= LEAF_THRESHOLD) {

                nll[curr] = LEAF;
                nbl[curr] = (long) bucketLen << 32 | (long) len;

                growBucket(len);
                for (int i = offset, end = offset + len; i < end; i++) {
                    bxl[bucketLen] = coordX[data[i]];
                    byl[bucketLen] = coordY[data[i]];
                    bzl[bucketLen] = coordZ[data[i]];
                    bucketLen++;
                }
            } else {

                final int axis = depth % 3 == 0 ? (int) AXIS_X : depth % 3 == 1 ? (int) AXIS_Z : (int) AXIS_Y;
                final int median = (len - 1) / 2;
                quickSelect(data, map[axis], offset, offset + len - 1, offset + median);
                final int pivot = data[offset + median];
                nsl[curr] = axis == AXIS_X ? coordX[pivot] : axis == AXIS_Y ? coordY[pivot] : coordZ[pivot];
                nll[curr] = LEFT_MASK | RIGHT_MASK | (long) axis;

                stack.push(new Node(curr, false, offset + median + 1, len - median - 1, depth + 1));
                stack.push(new Node(curr, true, offset, median + 1, depth + 1));
            }
            if (n.parent >= 0) {
                if (n.left) {
                    nll[n.parent] &= AXIS_MASK | RIGHT_MASK;
                    nll[n.parent] |= (long) curr << 2;
                } else {
                    nll[n.parent] &= AXIS_MASK | LEFT_MASK;
                    nll[n.parent] |= (long) curr << 32;
                }
            }
        }
    }

    private void insertionSort(int[] indices, double[] coord, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            int key = indices[i];
            double val = coord[key];
            int j = i - 1;

            while (j >= left && coord[indices[j]] > val) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = key;
        }
    }

    private void quickSelect(int[] indices, double[] coord, int left, int right, int k) {
        while (left < right) {
            if (right - left < INSERTION_SORT) {
                insertionSort(indices, coord, left, right);
                return;
            }
            int mid = left + (right - left) / 2;
            int a = indices[left], b = indices[mid], c = indices[right];
            double va = coord[a], vb = coord[b], vc = coord[c];
            int pivotIdx = (va < vb)
                ? (vb < vc ? mid : (va < vc ? right : left))
                : (va < vc ? left : (vb < vc ? right : mid));
            swap(indices, pivotIdx, left);
            double pivot = coord[indices[left]];

            int i = left;
            int j = right + 1;

            while (true) {
                while (++i <= right && coord[indices[i]] < pivot) ;
                while (--j > left && coord[indices[j]] > pivot) ;
                if (i >= j) break;
                swap(indices, i, j);
            }

            swap(indices, left, j);
            int p = j;
            if (p == k) {
                return;
            } else if (k < p) {
                right = p - 1;
            } else {
                left = p + 1;
            }
        }
    }

    private void swap(int[] a, int i, int j) {
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    private void reset() {
        nodeLen = 0;
        bucketLen = 0;
    }

    private void grow() {
        int capacity = nodeLen + 1;
        if (capacity < nsl.length) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_CAP) {
            capacity = INITIAL_CAP;
        }
        nsl = DoubleArrays.forceCapacity(nsl, capacity, nodeLen);
        nll = LongArrays.forceCapacity(nll, capacity, nodeLen);
        nbl = LongArrays.forceCapacity(nbl, capacity, nodeLen);
    }

    private void growBucket(int capacity) {
        capacity = bucketLen + capacity;
        if (capacity < bxl.length) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_CAP) {
            capacity = INITIAL_CAP;
        }
        bxl = DoubleArrays.forceCapacity(bxl, capacity, bucketLen);
        byl = DoubleArrays.forceCapacity(byl, capacity, bucketLen);
        bzl = DoubleArrays.forceCapacity(bzl, capacity, bucketLen);
    }

    private double nearest(final double tx, final double ty, final double tz, double dist) {
        if (nodeLen == 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (search.length < Math.max(64, nodeLen * 4)) {
            search = new int[Math.max(64, nodeLen * 4)];
        }
        if (SIMD) {
            return DespawnVectorAPI.nearest(search, nsl, nll, nbl, bxl, byl, bzl, tx, ty, tz, dist);
        }
        final int[] stack = this.search;
        final double[] nsl = this.nsl;
        final long[] nll = this.nll;
        final double[] bxl = this.bxl;
        final double[] byl = this.byl;
        final double[] bzl = this.bzl;
        final long[] nbl = this.nbl;
        int i = 0;
        stack[i++] = 0;
        while (i != 0) {
            final int idx = stack[--i];
            final long data = nll[idx];
            if (data != LEAF) {
                final long axis = data & AXIS_MASK;
                final double delta = (axis == AXIS_X ? tx : axis == AXIS_Y ? ty : tz) - nsl[idx];
                final long sign = Double.doubleToRawLongBits(delta) & SIGN_BIT;
                final long sMask = sign >> 63; // -1L or 0L
                final boolean leftValid = (data & LEFT_MASK) != LEFT_MASK;
                final boolean rightValid = (data & RIGHT_MASK) != RIGHT_MASK;
                final boolean pushNode = (sign == SIGN_BIT & leftValid) | ((sign == 0L) & rightValid);
                final boolean pushOther = ((sign == 0L) & leftValid) | (sign == SIGN_BIT & rightValid);
                final long node = (sMask & ((data & LEFT_MASK) >>> 2)) | (~sMask & (data >>> 32));
                final long other = (sMask & (data >>> 32)) | (~sMask & ((data & LEFT_MASK) >>> 2));
                if (pushNode) {
                    stack[i++] = (int) node;
                }
                if (pushOther && delta * delta < dist) {
                    stack[i++] = (int) other;
                }
            } else {
                final long bucket = nbl[idx];
                int start = (int) (bucket >>> 32);
                final int end = start + (int) (bucket & 0xffffffffL);
                for (; start < end; start++) {
                    final double dx = bxl[start] - tx;
                    final double dy = byl[start] - ty;
                    final double dz = bzl[start] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    dist = Math.min(dist, d2);
                }
            }
        }
        return dist;
    }

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }

    private static final class Stack {

        private Node[] a;
        private int i;

        private Stack(int capacity) {
            a = new Node[capacity];
            i = 0;
        }

        private boolean isEmpty() {
            return i == 0;
        }

        private void push(Node value) {
            if (i == a.length) {
                grow();
            }
            a[i++] = value;
        }

        private Node pop() {
            return a[--i];
        }

        private void grow() {
            Node[] b = new Node[a.length << 1];
            System.arraycopy(a, 0, b, 0, i);
            a = b;
        }
    }

    public void tick(ServerLevel world, EntityTickList entityTickList) {
        final ServerPlayer[] playerArr = world.players().toArray(EMPTY_PLAYERS);
        final ServerPlayer[] list = new ServerPlayer[playerArr.length];
        int newSize = 0;
        for (ServerPlayer player1 : playerArr) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player1)) {
                list[newSize++] = player1;
            }
        }
        double[] pxl = new double[newSize];
        double[] pyl = new double[newSize];
        double[] pzl = new double[newSize];
        for (int i = 0; i < newSize; i++) {
            pxl[i] = list[i].getX();
            pyl[i] = list[i].getY();
            pzl[i] = list[i].getZ();
        }
        build(pxl, pyl, pzl);
        entityTickList.forEach(Entity::leafCheckDespawn);
        reset();
    }

    public void checkDespawn(Mob mob) {
        final double x = mob.getX();
        final double y = mob.getY();
        final double z = mob.getZ();
        final int i = mob.getType().getCategory().ordinal();
        final double dist = nearest(x, y, z, hard[i]);
        if (dist == Double.POSITIVE_INFINITY) {
            return;
        }

        if (dist >= hard[i] && mob.removeWhenFarAway(dist)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (dist > sort[i]) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(dist)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }
}
