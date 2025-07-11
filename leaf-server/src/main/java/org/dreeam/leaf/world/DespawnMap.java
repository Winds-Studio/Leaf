package org.dreeam.leaf.world;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.DespawnRange;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
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
    private static final ServerPlayer[] EMPTY_PLAYER_ARRAY = {};
    private static final boolean FMA = LeafBootstrap.enableFMA;
    private static final int LEAF_THRESHOLD = 8;
    private static final int INITIAL_DEQUE_SIZE = 16;

    private ServerPlayer[] pl = EMPTY_PLAYER_ARRAY;
    private double[] pxl = {};
    private double[] pyl = {};
    private double[] pzl = {};
    private final LongDeque search = new LongDeque();
    private final Deque stack = new Deque();
    private final DoubleArrayList nxl = new DoubleArrayList();
    private final DoubleArrayList nyl = new DoubleArrayList();
    private final DoubleArrayList nzl = new DoubleArrayList();
    private final IntArrayList lnl = new IntArrayList();
    private final IntArrayList rnl = new IntArrayList();
    private final BooleanArrayList leaf = new BooleanArrayList();
    private final IntArrayList nbl = new IntArrayList();
    private final IntArrayList nbi = new IntArrayList();
    private final IntArrayList nbs = new IntArrayList();
    public boolean enabled = false;

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

    private void build(ServerLevel world) {
        final ServerPlayer[] playerArr = world.players().toArray(EMPTY_PLAYER_ARRAY);
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
        int nodeIndex = 0;
        assert nxl.isEmpty();
        while (!stack.isEmpty()) {
            final Node n = stack.poll();
            final int depth = n.depth;
            final int offset = n.offset;
            final int len = n.length;
            if (len <= LEAF_THRESHOLD) {
                nbi.add(nbl.size());
                nbs.add(len);
                nbl.addElements(nbl.size(), data, offset, len);
                nxl.add(0.0);
                nyl.add(0.0);
                nzl.add(0.0);
                lnl.add(-1);
                rnl.add(-1);
                leaf.add(true);
            } else {
                final int axis = depth % 3;
                IntArrays.quickSort(data, offset, offset + len, axis == 0 ? mx : axis == 1 ? my : mz);

                final int median = len / 2;
                final int pivot = data[offset + median];

                nxl.add(pxl[pivot]);
                nyl.add(pyl[pivot]);
                nzl.add(pzl[pivot]);
                lnl.add(-1);
                rnl.add(-1);
                leaf.add(false);
                nbs.add(0);
                nbi.add(0);
                stack.push(new Node(nodeIndex, true, offset, median, depth + 1));
                stack.push(new Node(nodeIndex, false, offset + median + 1, len - median - 1, depth + 1));
            }
            if (n.parent >= 0) {
                if (n.left) {
                    lnl.set(n.parent, nodeIndex);
                } else {
                    rnl.set(n.parent, nodeIndex);
                }
            }
            nodeIndex++;
        }
        stack.clear();
    }

    private void reset() {
        pl = EMPTY_PLAYER_ARRAY;
        nxl.clear();
        nyl.clear();
        nzl.clear();
        lnl.clear();
        rnl.clear();
        leaf.clear();
        nbl.clear();
        nbi.clear();
        nbs.clear();
    }

    private record Node(int parent, boolean left, int offset, int length, int depth) {
    }

    private ServerPlayer nearest(final double tx, final double ty, final double tz) {
        if (nxl.isEmpty()) {
            return null;
        }

        int nearest = -1;
        double dist = Double.POSITIVE_INFINITY;
        final double[] nxl = this.nxl.elements();
        final double[] nyl = this.nyl.elements();
        final double[] nzl = this.nzl.elements();
        final int[] lnl = this.lnl.elements();
        final int[] rnl = this.rnl.elements();
        final boolean[] leaf = this.leaf.elements();
        final int[] nbl = this.nbl.elements();
        final int[] nbi = this.nbi.elements();
        final int[] nbs = this.nbs.elements();
        search.push(0L);
        while (!search.isEmpty()) {
            final long frame = search.poll();
            final int idx = (int) frame;
            final int depth = (int) (frame >>> 32);

            if (leaf[idx]) {
                final int bucket = nbi[idx];
                final int end = bucket + nbs[idx];
                for (int i = bucket; i < end; i++) {
                    final int p = nbl[i];
                    final double dx = this.pxl[p] - tx;
                    final double dy = this.pyl[p] - ty;
                    final double dz = this.pzl[p] - tz;
                    final double d2 = FMA ? Math.fma(dz, dz, Math.fma(dy, dy, dx * dx)) : dx * dx + dy * dy + dz * dz;
                    if (d2 < dist) {
                        dist = d2;
                        nearest = p;
                    }
                }
            } else {
                final int axis = depth % 3;
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
                    search.push((long) nearIdx | (long) depth + 1L << 32);
                }
                if (farIdx != -1 && delta * delta < dist) {
                    search.push((long) farIdx | (long) depth + 1L << 32);
                }
            }
        }
        search.clear();
        return nearest != -1 ? this.pl[nearest] : null;
    }

    private final static class Deque {
        private Node[] array;
        private int length;
        private int start;
        private int end;

        private Deque() {
            array = new Node[INITIAL_DEQUE_SIZE];
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

    private final static class LongDeque {
        private long[] array;
        private int length;
        private int start;
        private int end;

        private LongDeque() {
            array = new long[INITIAL_DEQUE_SIZE];
            length = array.length;
        }

        private void clear() {
            start = 0;
            end = 0;
        }

        private boolean isEmpty() {
            return end == start;
        }

        private long poll() {
            final long t = array[start];
            if (++start == length) start = 0;
            return t;
        }

        private void push(final long node) {
            array[end++] = node;
            if (end == length) end = 0;
            if (end == start) resize(length, 2 * length);
        }

        private void resize(final int size, final int newLength) {
            final long[] newArray = new long[newLength];
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
        final double distanceSquared = dxSqr + dzSqr + dySqr;
        if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dyAbs) && mob.removeWhenFarAway(distanceSquared)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dyAbs)) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(distanceSquared)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }
}
