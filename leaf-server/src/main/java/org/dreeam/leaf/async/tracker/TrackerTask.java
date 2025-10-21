package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.util.EntitySlice;

import java.util.concurrent.Callable;

public record TrackerTask(ServerLevel world, EntitySlice entities) implements Callable<TrackerCtx> {

    @Override
    public TrackerCtx call() throws Exception {
        final TrackerCtx ctx = new TrackerCtx(this.world);
        final Entity[] raw = entities.array();
        for (int i = entities.start(); i < entities.end(); i++) {
            final Entity entity = raw[i];
            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            // removed in world if null
            if (tracker == null) {
                continue;
            }
            if (tracker.getClass() != ChunkMap.TrackedEntity.class) {
                ctx.citizensEntity(entity);
                continue;
            }
            ChunkData chunkData = ((ChunkSystemEntity) entity).moonrise$getChunkData();
            // removed in world if null
            if (chunkData == null) {
                continue;
            }
            tracker.leafTick(ctx, chunkData.nearbyPlayers);
            boolean flag = false;
            if (tracker.moonrise$hasPlayers()) {
                flag = true;
            } else {
                FullChunkStatus status = ((ChunkSystemEntity) entity).moonrise$getChunkStatus();
                // removed in world if null
                if (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    flag = true;
                }
            }
            if (flag) {
                tracker.serverEntity.leafSendChanges(ctx, tracker);
            }
        }
        return ctx;
    }
}
