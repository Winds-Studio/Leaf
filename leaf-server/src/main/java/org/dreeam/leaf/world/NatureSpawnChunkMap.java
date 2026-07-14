package org.dreeam.leaf.world;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import org.dreeam.leaf.util.KDTree3D;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public final class NatureSpawnChunkMap {
    /// breadth-first search
    ///
    /// 0 4 12 28 48 80 112 148 196
    private static final long[][] TABLE_BFS = {
        {0L},
        {0L, 4294967295L, -4294967296L, 4294967296L, 1L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, 4294967294L, -8589934592L, 8589934592L, 2L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, 4294967293L, -12884901888L, 12884901888L, 3L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, -4294967299L, -3L, -8589934594L, -8589934593L, 4294967293L, 8589934589L, -12884901888L, -12884901887L, 12884901885L, 17179869182L, 17179869183L, 12884901888L, 12884901889L, -12884901886L, 12884901890L, -8589934589L, -4294967293L, 3L, 4294967299L, 8589934595L, 4294967292L, -17179869184L, 17179869184L, 4L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, -8589934595L, -4294967299L, -3L, -8589934594L, -8589934593L, 4294967293L, 8589934589L, -12884901888L, -12884901887L, 12884901885L, 17179869181L, 17179869182L, 17179869183L, 12884901888L, 12884901889L, -12884901886L, 12884901890L, -12884901885L, -8589934589L, -4294967293L, 3L, 4294967299L, 8589934595L, 12884901891L, -8589934596L, -4294967300L, -12884901891L, -12884901890L, -4L, 4294967292L, -12884901889L, -17179869184L, 8589934588L, 12884901884L, -17179869183L, -17179869182L, 17179869180L, 21474836477L, 21474836478L, 21474836479L, 17179869184L, 17179869185L, 17179869186L, -17179869181L, 17179869187L, -12884901884L, -8589934588L, -4294967292L, 4L, 4294967300L, 8589934596L, 12884901892L, 4294967291L, -21474836480L, 21474836480L, 5L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, -8589934595L, -4294967299L, -3L, -8589934594L, -8589934593L, 4294967293L, 8589934589L, -12884901888L, -12884901887L, 12884901885L, 17179869181L, 17179869182L, 17179869183L, 12884901888L, 12884901889L, -12884901886L, 12884901890L, -12884901885L, -8589934589L, -4294967293L, 3L, 4294967299L, 8589934595L, 12884901891L, -12884901892L, -8589934596L, -4294967300L, -12884901891L, -12884901890L, -4L, 4294967292L, -12884901889L, -17179869184L, 8589934588L, 12884901884L, -17179869183L, -17179869182L, 17179869180L, 21474836476L, 21474836477L, 21474836478L, 21474836479L, 17179869184L, 17179869185L, 17179869186L, -17179869181L, 17179869187L, -17179869180L, -12884901884L, -8589934588L, -4294967292L, 4L, 4294967300L, 8589934596L, 12884901892L, 17179869188L, -8589934597L, -17179869187L, -4294967301L, -5L, -17179869186L, -17179869185L, 4294967291L, 8589934587L, -21474836480L, -21474836479L, 12884901883L, 17179869179L, -21474836478L, -21474836477L, 25769803773L, 25769803774L, 25769803775L, 21474836480L, 21474836481L, 21474836482L, 21474836483L, -12884901883L, -8589934587L, -4294967291L, 5L, 4294967301L, 8589934597L, 12884901893L, 4294967290L, -25769803776L, 25769803776L, 6L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, -8589934595L, -4294967299L, -3L, -8589934594L, -8589934593L, 4294967293L, 8589934589L, -12884901888L, -12884901887L, 12884901885L, 17179869181L, 17179869182L, 17179869183L, 12884901888L, 12884901889L, -12884901886L, 12884901890L, -12884901885L, -8589934589L, -4294967293L, 3L, 4294967299L, 8589934595L, 12884901891L, -12884901892L, -8589934596L, -4294967300L, -12884901891L, -12884901890L, -4L, 4294967292L, -12884901889L, -17179869184L, 8589934588L, 12884901884L, -17179869183L, -17179869182L, 17179869180L, 21474836476L, 21474836477L, 21474836478L, 21474836479L, 17179869184L, 17179869185L, 17179869186L, -17179869181L, 17179869187L, -17179869180L, -12884901884L, -8589934588L, -4294967292L, 4L, 4294967300L, 8589934596L, 12884901892L, 17179869188L, -12884901893L, -8589934597L, -17179869188L, -17179869187L, -4294967301L, -5L, -17179869186L, -17179869185L, 4294967291L, 8589934587L, -21474836480L, -21474836479L, 12884901883L, 17179869179L, -21474836478L, -21474836477L, 21474836475L, 25769803772L, 25769803773L, 25769803774L, 25769803775L, 21474836480L, 21474836481L, 21474836482L, 21474836483L, -21474836476L, 21474836484L, -17179869179L, -12884901883L, -8589934587L, -4294967291L, 5L, 4294967301L, 8589934597L, 12884901893L, 17179869189L, -8589934598L, -4294967302L, -21474836483L, -21474836482L, -6L, 4294967290L, -21474836481L, -25769803776L, 8589934586L, 12884901882L, -25769803775L, -25769803774L, 17179869178L, -25769803773L, 30064771069L, 30064771070L, 30064771071L, 25769803776L, 25769803777L, 25769803778L, 25769803779L, -12884901882L, -8589934586L, -4294967290L, 6L, 4294967302L, 8589934598L, 12884901894L, 4294967289L, -30064771072L, 30064771072L, 7L},
        {0L, -1L, 4294967295L, 8589934591L, -4294967296L, 4294967296L, -4294967295L, 1L, 4294967297L, -4294967298L, -2L, 4294967294L, -4294967297L, -8589934592L, 8589934590L, 12884901886L, 12884901887L, 8589934592L, -8589934591L, 8589934593L, -8589934590L, -4294967294L, 2L, 4294967298L, 8589934594L, -8589934595L, -4294967299L, -3L, -8589934594L, -8589934593L, 4294967293L, 8589934589L, -12884901888L, -12884901887L, 12884901885L, 17179869181L, 17179869182L, 17179869183L, 12884901888L, 12884901889L, -12884901886L, 12884901890L, -12884901885L, -8589934589L, -4294967293L, 3L, 4294967299L, 8589934595L, 12884901891L, -12884901892L, -8589934596L, -4294967300L, -12884901891L, -12884901890L, -4L, 4294967292L, -12884901889L, -17179869184L, 8589934588L, 12884901884L, -17179869183L, -17179869182L, 17179869180L, 21474836476L, 21474836477L, 21474836478L, 21474836479L, 17179869184L, 17179869185L, 17179869186L, -17179869181L, 17179869187L, -17179869180L, -12884901884L, -8589934588L, -4294967292L, 4L, 4294967300L, 8589934596L, 12884901892L, 17179869188L, -17179869189L, -12884901893L, -8589934597L, -17179869188L, -17179869187L, -4294967301L, -5L, -17179869186L, -17179869185L, 4294967291L, 8589934587L, -21474836480L, -21474836479L, 12884901883L, 17179869179L, -21474836478L, -21474836477L, 21474836475L, 25769803771L, 25769803772L, 25769803773L, 25769803774L, 25769803775L, 21474836480L, 21474836481L, 21474836482L, 21474836483L, -21474836476L, 21474836484L, -21474836475L, -17179869179L, -12884901883L, -8589934587L, -4294967291L, 5L, 4294967301L, 8589934597L, 12884901893L, 17179869189L, 21474836485L, -17179869190L, -12884901894L, -21474836485L, -21474836484L, -8589934598L, -4294967302L, -21474836483L, -21474836482L, -6L, 4294967290L, -21474836481L, -25769803776L, 8589934586L, 12884901882L, -25769803775L, -25769803774L, 17179869178L, 21474836474L, -25769803773L, -25769803772L, 25769803770L, 30064771067L, 30064771068L, 30064771069L, 30064771070L, 30064771071L, 25769803776L, 25769803777L, 25769803778L, 25769803779L, 25769803780L, -25769803771L, 25769803781L, -21474836474L, -17179869178L, -12884901882L, -8589934586L, -4294967290L, 6L, 4294967302L, 8589934598L, 12884901894L, 17179869190L, 21474836486L, -8589934599L, -25769803779L, -4294967303L, -7L, -25769803778L, -25769803777L, 4294967289L, 8589934585L, -30064771072L, -30064771071L, 12884901881L, 17179869177L, -30064771070L, -30064771069L, 34359738365L, 34359738366L, 34359738367L, 30064771072L, 30064771073L, 30064771074L, 30064771075L, -12884901881L, -8589934585L, -4294967289L, 7L, 4294967303L, 8589934599L, 12884901895L, 4294967288L, -34359738368L, 34359738368L, 8L}
    };
    private static final int MAX_RADIUS = 8;
    private static final int SIZE_RADIUS = 9;
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private static final double MAX_DIST = 16384.0;

    private final LongArrayList[] centersByRadius;
    private final LongSet set;
    private final KDTree3D tree;
    private boolean ready;

    private static final class LongSet extends LongOpenHashSet {
        private long[] key() {
            return this.key;
        }

        private boolean containsNull() {
            return this.containsNull;
        }

        private int n() {
            return this.n;
        }
    }

    public NatureSpawnChunkMap() {
        this.centersByRadius = new LongArrayList[SIZE_RADIUS];
        for (int i = 0; i < SIZE_RADIUS; i++) {
            this.centersByRadius[i] = new LongArrayList();
        }
        this.set = new LongSet();
        this.tree = new KDTree3D();
        this.ready = false;
    }

    public void clear() {
        if (!this.ready) {
            return;
        }
        for (final LongArrayList center : this.centersByRadius) {
            center.clear();
        }
        this.set.clear();
        this.ready = false;
    }

    ///  empty => `POSITIVE_INFINITY`
    public double nearest(final ServerLevel world, final double x, final double y, final double z) {
        if (ready) {
            return tree.nearestSqr(x, y, z, MAX_DIST);
        } else {
            Player player = world.getNearestPlayer(x, y, z, -1.0, world.purpurConfig.mobSpawningIgnoreCreativePlayers);
            return player == null ? Double.POSITIVE_INFINITY : player.distanceToSqr(x, y, z);
        }
    }

    public void tick(final ServerLevel world, final List<LevelChunk> out) {
        ServerPlayer[] players = initPlayer(world);
        for (int index = 0; index < SIZE_RADIUS; index++) {
            buildBfs(index);
        }
        buildKdTree(world.purpurConfig.mobSpawningIgnoreCreativePlayers, players);
        collectSpawningChunks(world.getChunkSource().fullChunksNonSync, this.set, out);
        this.ready = true;
    }

    private void buildBfs(final int index) {
        LongArrayList list = this.centersByRadius[index];
        LongSet set = this.set;
        int size = deduplicate(list);
        long[] raw = list.elements();
        long[] offsets = TABLE_BFS[index];
        for (int i = 0; i < size; i++) {
            long center = raw[i];
            int cx = CoordinateUtils.getChunkX(center);
            int cz = CoordinateUtils.getChunkZ(center);
            for (final long offset : offsets) {
                int dx = CoordinateUtils.getChunkX(offset);
                int dz = CoordinateUtils.getChunkZ(offset);
                set.add(CoordinateUtils.getChunkKey(cx + dx, cz + dz));
            }
        }
    }

    private static int deduplicate(final LongArrayList list) {
        int n = list.size();
        if (n == 0) {
            return 0;
        }
        list.unstableSort(null);
        long[] raw = list.elements();
        int size = 0;
        for (int i = 1; i < n; i++) {
            long current = raw[i];
            long last = raw[size];
            if (current != last) {
                size++;
                raw[size] = current;
            }
        }
        return size + 1;
    }

    private ServerPlayer[] initPlayer(final ServerLevel world) {
        ServerPlayer[] players = world.players().toArray(EMPTY_PLAYERS);
        for (final ServerPlayer player : players) {
            if (player.isSpectator()) {
                continue;
            }
            PlayerNaturallySpawnCreaturesEvent event = player.playerNaturallySpawnedEvent;
            if (event == null || event.isCancelled()) {
                continue;
            }
            int range = event.getSpawnRadius();
            if (range > MAX_RADIUS) {
                range = MAX_RADIUS;
            } else if (range < 0) {
                continue;
            }
            this.centersByRadius[range].add(player.chunkPosition().longKey());
        }
        return players;
    }

    private void buildKdTree(final boolean ignoreCreativePlayers, final ServerPlayer[] players) {
        double[] pxl = new double[players.length];
        double[] pyl = new double[players.length];
        double[] pzl = new double[players.length];
        int i = 0;
        for (final ServerPlayer p : players) {
            if (!p.isSpectator() && !(ignoreCreativePlayers && p.isCreative())) {
                pxl[i] = p.getX();
                pyl[i] = p.getY();
                pzl[i] = p.getZ();
                i++;
            }
        }
        final int[] indices = new int[i];
        for (int j = 0; j < i; j++) {
            indices[j] = j;
        }
        this.tree.build(new double[][]{pxl, pyl, pzl}, indices);
    }

    private static void collectSpawningChunks(final ChunkCache<LevelChunk> chunks,
                                              final LongSet set,
                                              final List<LevelChunk> out) {
        long[] key = set.key();
        int n = set.n();
        if (set.containsNull()) {
            LevelChunk chunk = chunks.getMiss(key[n]);
            if (chunk != null && chunk.moonrise$getChunkHolder().getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                out.add(chunk);
            }
        }
        for (int pos = n; pos-- != 0; ) {
            if ((key[pos]) != 0L) {
                LevelChunk chunk = chunks.getMiss(key[pos]);
                if (chunk != null && chunk.moonrise$getChunkHolder().getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    out.add(chunk);
                }
            }
        }
    }
}
