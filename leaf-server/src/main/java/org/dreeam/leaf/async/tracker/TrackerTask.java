package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dreeam.leaf.util.TrackerSlice;

import java.util.concurrent.Callable;

public record TrackerTask(ServerLevel world, TrackerSlice entities, Reference2ReferenceOpenHashMap<ChunkMap.TrackedEntity, TrackerInput> cap) implements Callable<TrackerCtx> {

    @Override
    public TrackerCtx call() throws Exception {
        final TrackerCtx ctx = new TrackerCtx(this.world);
        final Entity[] raw = entities.array();
        for (int i = entities.start(); i < entities.end(); i++) {
            final Entity entity = raw[i];
            final ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
            if (tracker == null || !tracker.serverEntity.leaf$isInLevel(this.world)) {
                continue;
            }
            if (tracker.getClass() != ChunkMap.TrackedEntity.class) {
                ctx.pluginEntity(tracker);
                continue;
            }

            TrackerInput input = tracker.serverEntity.leaf$trackerInput;
            TrackerInput patch = cap.get(tracker);
            if (patch != null) {
                input.apply(patch);
            }

            ChunkData chunkData = ((ChunkSystemEntity) entity).moonrise$getChunkData();
            boolean sendChanges = tracker.leaf$tick(ctx, chunkData == null ? null : chunkData.nearbyPlayers);
            if (!sendChanges) {
                net.minecraft.server.level.FullChunkStatus status = entity.moonrise$getChunkStatus();
                sendChanges = status != null && status.isOrAfter(net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
            }
            if (sendChanges || entity.needsSync) {
                tracker.serverEntity.leaf$sendChanges(ctx, tracker, input, false);
            }
        }
        return ctx;
    }
}
