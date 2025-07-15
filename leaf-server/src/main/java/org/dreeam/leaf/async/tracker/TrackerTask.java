package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.util.EntitySlice;

import java.util.concurrent.Callable;

public final class TrackerTask implements Callable<TrackerCtx> {
    public final ServerLevel world;
    private final EntitySlice entities;

    public TrackerTask(ServerLevel world, EntitySlice trackerEntities) {
        this.world = world;
        this.entities = trackerEntities;
    }

    @Override
    public TrackerCtx call() throws Exception {
        NearbyPlayers nearbyPlayers = world.moonrise$getNearbyPlayers();
        TrackerCtx ctx = new TrackerCtx(this.world);
        final Entity[] raw = entities.array();
        for (int i = entities.start(); i < entities.end(); i++) {
            final Entity entity = raw[i];
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            if (tracker.getClass() != ChunkMap.TrackedEntity.class) {
                ctx.citizensEntity(entity);
                continue;
            }
            NearbyPlayers.TrackedChunk trackedChunk = nearbyPlayers.getChunk(entity.chunkPosition());

            tracker.leafTick(ctx, trackedChunk);
            boolean flag = false;
            if (tracker.moonrise$hasPlayers()) {
                flag = true;
            } else {
                // may read old value
                FullChunkStatus status = ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkStatus();
                // removed in world
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
