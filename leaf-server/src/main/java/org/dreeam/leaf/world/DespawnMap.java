package org.dreeam.leaf.world;

import gg.pufferfish.pufferfish.simd.SIMDDetection;
import io.papermc.paper.configuration.WorldConfiguration;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.LeafBootstrap;

import java.util.Map;
import java.util.OptionalInt;

public final class DespawnMap {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final long[] EMPTY_LONGS = {};
    private static final int[] EMPTY_INTS = {};
    static final boolean FMA = LeafBootstrap.enableFMA;
    private static final boolean SIMD = SIMDDetection.isEnabled();
    private static final int LEAF_THRESHOLD = SIMD ? DespawnVectorAPI.DOUBLE_VECTOR_LENGTH : 4;
    private static final int INITIAL_CAP = 8;
    static final long INTERNAL = -1L;
    static final long AXIS_X = 0L;
    static final long AXIS_Y = 1L;

    /// Stack for tree construction
    private final Stack stack = new Stack(INITIAL_CAP);
    /// Stack for tree traversal
    private int[] search = EMPTY_INTS;

    private int nodeLen = 0;
    private int bucketLen = 0;

    /// Node X coordinates for each internal node
    private double[] nxl = EMPTY_DOUBLES;
    /// Node Y coordinates for each internal node
    private double[] nyl = EMPTY_DOUBLES;
    /// Node Z coordinates for each internal node
    private double[] nzl = EMPTY_DOUBLES;
    /// Offsets(32) Lengths(32) for each player list of leaf nodes
    /// `INTERNAL(-1)` indicating internal node
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
        for (Map.Entry<MobCategory, WorldConfiguration.Entities.Spawning.DespawnRangePair> mobCategoryDespawnRangePairEntry : worldConfiguration.entities.spawning.despawnRanges.entrySet()) {
            OptionalInt a = mobCategoryDespawnRangePairEntry.getValue().soft().verticalLimit.value();
            OptionalInt b = mobCategoryDespawnRangePairEntry.getValue().soft().horizontalLimit.value();
            OptionalInt c = mobCategoryDespawnRangePairEntry.getValue().hard().verticalLimit.value();
            OptionalInt d = mobCategoryDespawnRangePairEntry.getValue().hard().horizontalLimit.value();
            if (a.isPresent() && b.isPresent() && a.getAsInt() == b.getAsInt()) {
                sort[mobCategoryDespawnRangePairEntry.getKey().ordinal()] = a.getAsInt();
            }
            if (c.isPresent() && d.isPresent() && c.getAsInt() == d.getAsInt()) {
                hard[mobCategoryDespawnRangePairEntry.getKey().ordinal()] = c.getAsInt();
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

    private void build(ServerLevel world) {
        final ServerPlayer[] playerArr = world.players().toArray(EMPTY_PLAYERS);
        final ServerPlayer[] list = new ServerPlayer[playerArr.length];
        int newSize = 0;
        for (ServerPlayer player1 : playerArr) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player1)) {
                list[newSize++] = player1;
            }
        }
        ServerPlayer[] pl = ObjectArrays.setLength(list, newSize);
        double[] pxl = new double[pl.length];
        double[] pyl = new double[pl.length];
        double[] pzl = new double[pl.length];
        for (int i = 0; i < pl.length; i++) {
            pxl[i] = pl[i].getX();
            pyl[i] = pl[i].getY();
            pzl[i] = pl[i].getZ();
        }
        final double[][] ml = {pxl, pyl, pzl};
        final int[] data = new int[pxl.length];
        for (int i = 0; i < pxl.length; i++) {
            data[i] = i;
        }
        stack.push(new Node(-1, false, 0, pxl.length, 0));
        while (!stack.isEmpty()) {
            grow();

            final Node n = stack.pop();
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            final int curr = nodeLen++;
            if (len <= LEAF_THRESHOLD) {
                nbl[curr] = (long) bucketLen << 32 | (long) len;
                growBucket(len);
                for (int i = 0; i < len; i++) {
                    int p = data[offset + i];
                    bxl[bucketLen + i] = pxl[p];
                    byl[bucketLen + i] = pyl[p];
                    bzl[bucketLen + i] = pzl[p];
                }
                bucketLen += len;
                nll[curr] = 0x3fff_ffff_ffff_ffffL;
            } else {
                final int axis = depth % 3;
                final int median = len / 2;
                quickSelect(data, offset, offset + len - 1, offset + median, ml[axis]);
                final int pivot = data[offset + median];

                nbl[curr] = INTERNAL;
                nxl[curr] = pxl[pivot];
                nyl[curr] = pyl[pivot];
                nzl[curr] = pzl[pivot];
                nll[curr] = 0x3fff_ffff_ffff_fffcL | ((long) axis);
                stack.push(new Node(curr, true, offset, median, depth + 1));
                stack.push(new Node(curr, false, offset + median + 1, len - median - 1, depth + 1));
            }
            if (n.parent >= 0) {
                if (n.left) {
                    nll[n.parent] &= 0x3fffffff00000003L;
                    nll[n.parent] |= (long) curr << 2;
                } else {
                    nll[n.parent] &= 0xffffffffL;
                    nll[n.parent] |= (long) curr << 32;
                }
            }
        }
    }

    private void swap(int[] data, int i, int j) {
        int tmp = data[i];
        data[i] = data[j];
        data[j] = tmp;
    }

    private void quickSelect(int[] data, int left, int right, int k, double[] coord) {
        while (left < right) {
            double pivotL = coord[data[left]];
            double pivotR = coord[data[right]];

            if (right - left == 1) {
                if (pivotL > pivotR) {
                    swap(data, left, right);
                }
                return;
            }

            if (pivotL > pivotR) {
                swap(data, left, right);
                double tmp = pivotL;
                pivotL = pivotR;
                pivotR = tmp;
            }

            if (pivotL == pivotR) {
                int i = left + 1;
                int j = right;
                while (i <= j) {
                    while (i <= j && coord[data[i]] <= pivotL) i++;
                    while (i <= j && coord[data[j]] > pivotL) j--;
                    if (i < j) {
                        swap(data, i++, j--);
                    }
                }
                swap(data, left, j);
                int partitionIndex = j;
                if (partitionIndex == k) {
                    return;
                } else if (k < partitionIndex) {
                    right = partitionIndex - 1;
                } else {
                    left = partitionIndex + 1;
                }
                continue;
            }

            int lt = left + 1;
            int gt = right - 1;
            int i = left + 1;

            while (i <= gt) {
                double val = coord[data[i]];
                if (val < pivotL) {
                    swap(data, i++, lt++);
                } else if (val > pivotR) {
                    swap(data, i, gt--);
                } else {
                    i++;
                }
            }

            swap(data, left, --lt);
            swap(data, right, ++gt);

            if (k <= lt) {
                right = lt;
            } else if (k >= gt) {
                left = gt;
            } else {
                return;
            }
        }
    }

    private void reset() {
        nodeLen = 0;
        bucketLen = 0;
    }

    private void grow() {
        int capacity = nodeLen + 1;
        if (capacity < nxl.length) {
            return;
        }
        capacity += capacity >> 1;
        if (capacity < INITIAL_CAP) {
            capacity = INITIAL_CAP;
        }
        nxl = DoubleArrays.forceCapacity(nxl, capacity, nodeLen);
        nyl = DoubleArrays.forceCapacity(nyl, capacity, nodeLen);
        nzl = DoubleArrays.forceCapacity(nzl, capacity, nodeLen);
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

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }

    private double nearest(final double tx, final double ty, final double tz) {
        if (nodeLen == 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (search.length < Math.max(64, nodeLen * 4)) {
            search = new int[Math.max(64, nodeLen * 4)];
        }
        if (SIMD) {
            return DespawnVectorAPI.nearest(search, nxl, nyl, nzl, nll, nbl, bxl, byl, bzl, tx, ty, tz);
        }
        double dist = Double.POSITIVE_INFINITY;
        final int[] stack = this.search;
        final double[] nxl = this.nxl;
        final double[] nyl = this.nyl;
        final double[] nzl = this.nzl;
        final long[] nll = this.nll;
        final double[] bxl = this.bxl;
        final double[] byl = this.byl;
        final double[] bzl = this.bzl;
        final long[] nbl = this.nbl;
        int i = 0;
        stack[i++] = 0;
        while (i != 0) {
            final int idx = stack[--i];
            final long bucket = nbl[idx];
            if (bucket == INTERNAL) {
                final long data = nll[idx];
                final long axis = data & 0b11;
                final double delta = axis == AXIS_X ? tx - nxl[idx] : axis == AXIS_Y ? ty - nyl[idx] : tz - nzl[idx];
                final boolean negative = (Double.doubleToRawLongBits(delta) & 0x8000_0000_0000_0000L) == 0x8000_0000_0000_0000L;
                final long sMask = negative ? -1L : 0L;
                final boolean leftValid = (data & 0xfffffffcL) != 0xfffffffcL;
                final boolean rightValid = (data & 0x3fffffff00000000L) != 0x3fffffff00000000L;
                final long node = sMask & (data & 0xfffffffcL) >>> 2 | ~sMask & data >>> 32;
                final long other = sMask & data >>> 32 | ~sMask & (data & 0xfffffffcL) >>> 2;
                if ((negative & leftValid) | (!negative & rightValid)) {
                    stack[i++] = (int) node;
                }
                if ((!negative & leftValid) | (negative & rightValid) && delta * delta < dist) {
                    stack[i++] = (int) other;
                }
            } else {
                int start = (int) (bucket >>> 32);
                final int end = start + (int) (bucket & 0xffffffffL);
                for (; start < end; start++) {
                    final double dx = bxl[start] - tx;
                    final double dy = byl[start] - ty;
                    final double dz = bzl[start] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    if (d2 < dist) {
                        dist = d2;
                    }
                }
            }
        }
        return dist;
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
        build(world);
        entityTickList.forEach(Entity::leafCheckDespawn);
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
        if (dist > hard[i] && mob.removeWhenFarAway(dist)) {
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
