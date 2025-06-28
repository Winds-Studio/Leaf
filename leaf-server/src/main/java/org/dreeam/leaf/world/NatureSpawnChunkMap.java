package org.dreeam.leaf.world;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public class NatureSpawnChunkMap {
    private final LongArrayList[] chunkPositionsByRadius;
    private final LongOpenHashSet toCollect;
    private static final int MAX_RADIUS = 8;

    public NatureSpawnChunkMap() {
        this.chunkPositionsByRadius = new LongArrayList[MAX_RADIUS];
        for (int i = 0; i < MAX_RADIUS; i++) {
            chunkPositionsByRadius[i] = new LongArrayList();
        }
        this.toCollect = new LongOpenHashSet();
    }

    public void clear() {
        for (LongArrayList chunkPosition : chunkPositionsByRadius) {
            chunkPosition.clear();
        }
    }

    public void addPlayer(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        PlayerNaturallySpawnCreaturesEvent event = player.playerNaturallySpawnedEvent;
        if (event == null || event.isCancelled()) {
            return;
        }
        int range = event.getSpawnRadius();
        if (range > MAX_RADIUS || range < 1) return;
        this.chunkPositionsByRadius[range - 1].add(player.chunkPosition().longKey);
    }

    public void build() {
        this.toCollect.clear();
        for (int index = 0; index < MAX_RADIUS; index++) {
            LongArrayList list = chunkPositionsByRadius[index];
            int n = list.size();
            if (n == 0) {
                continue;
            }
            list.unstableSort(null);
            long[] centersRaw = list.elements();
            int size = 0;
            for (int i = 1; i < n; i++) {
                long current = centersRaw[i];
                long last = centersRaw[size];
                if (current != last) {
                    size++;
                    centersRaw[size] = current;
                }
            }
            size++;
            int radius = index + 1;
            int rsqr = radius * radius;
            for (int i = 0, j = size; i < j; i++) {
                long center = centersRaw[i];
                int cx = ChunkPos.getX(center);
                int cz = ChunkPos.getZ(center);
                int minX = cx - radius;
                int maxX = cx + radius;
                int minZ = cz - radius;
                int maxZ = cz + radius;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int dx = x - cx;
                        int dz = z - cz;
                        if (dx * dx + dz * dz <= rsqr) {
                            this.toCollect.add(ChunkPos.asLong(x, z));
                        }
                    }
                }
            }
        }
    }

    public void collectSpawningChunks(ReferenceList<LevelChunk> chunks, List<LevelChunk> out) {
        LevelChunk[] raw = chunks.getRawDataUnchecked();
        for (int i = 0, l = chunks.size(); i < l; i++) {
            LevelChunk chunk = raw[i];
            int chunkX = chunk.locX;
            int chunkZ = chunk.locZ;
            if (toCollect.contains(ChunkPos.asLong(chunkX, chunkZ))) {
                out.add(chunk);
            }
        }
    }
}
