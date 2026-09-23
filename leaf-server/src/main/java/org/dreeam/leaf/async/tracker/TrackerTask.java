package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.util.TrackerSlice;

import java.util.concurrent.Callable;

public record TrackerTask(ServerLevel world, TrackerSlice trackers, long batch) implements Callable<TrackerCtx> {

    @Override
    public TrackerCtx call() throws Exception {
        final TrackerCtx ctx = new TrackerCtx(this.world);
        final ChunkMap.TrackedEntity[] raw = trackers.array();
        for (int i = trackers.start(); i < trackers.end(); i++) {
            final ChunkMap.TrackedEntity tracker = raw[i];
            if (tracker.serverEntity.entity.moonrise$getTrackedEntity() != tracker) {
                continue;
            }
            final Entity entity = tracker.serverEntity.entity;
            tracker.serverEntity.leaf$trackingInput.collect(this.batch);
            if (tracker.getClass() != ChunkMap.TrackedEntity.class) {
                ctx.citizensEntity(tracker);
                continue;
            }
            ChunkData chunkData = ((ChunkSystemEntity) entity).moonrise$getChunkData();
            boolean sendChanges = tracker.leaf$tick(ctx, chunkData == null ? null : chunkData.nearbyPlayers);
            if (!sendChanges) {
                net.minecraft.server.level.FullChunkStatus status = entity.moonrise$getChunkStatus();
                sendChanges = status != null && status.isOrAfter(net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
            }
            if (sendChanges || entity.needsSync) {
                tracker.serverEntity.leaf$sendChanges(ctx, tracker, false);
            }
        }
        return ctx;
    }
}
