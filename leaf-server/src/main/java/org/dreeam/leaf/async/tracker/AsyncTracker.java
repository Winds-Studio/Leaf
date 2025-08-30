package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.dreeam.leaf.async.FixedThreadExecutor;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;
import org.dreeam.leaf.util.EntitySlice;

import java.util.concurrent.*;

public final class AsyncTracker {
    private static final String THREAD_NAME = "Leaf Async Tracker Thread";
    public static final boolean ENABLED = MultithreadedTracker.enabled;
    public static final int QUEUE = 1024;
    public static final int MIN_CHUNK = 16;
    public static final int THREADS = MultithreadedTracker.threads;
    public static final FixedThreadExecutor TRACKER_EXECUTOR = ENABLED ? new FixedThreadExecutor(
        THREADS,
        QUEUE,
        THREAD_NAME
    ) : null;

    private AsyncTracker() {
    }

    public static void init() {
        if (TRACKER_EXECUTOR == null || !ENABLED) {
            throw new IllegalStateException();
        }
    }

    public static void tick(ServerLevel world) {
        handlePlayerVelocity(world);
        ServerEntityLookup entityLookup = (ServerEntityLookup) world.moonrise$getEntityLookup();
        ca.spottedleaf.moonrise.common.list.ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
        int trackerEntitiesSize = trackerEntities.size();
        if (trackerEntitiesSize == 0) {
            return;
        }
        Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        Entity[] entities = new Entity[trackerEntitiesSize];
        System.arraycopy(trackerEntitiesRaw, 0, entities, 0, trackerEntitiesSize);
        EntitySlice slice = new EntitySlice(entities);
        EntitySlice[] slices = entities.length <= THREADS * MIN_CHUNK ? slice.chunks(MIN_CHUNK) : slice.splitEvenly(THREADS);
        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slices.length];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = TRACKER_EXECUTOR.submitOrRun(new TrackerTask(world, slices[i]));
        }
        TRACKER_EXECUTOR.unpack();
        world.trackerTask = futures;
    }

    private static void handlePlayerVelocity(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            if (!player.hurtMarked) {
                continue;
            }
            player.hurtMarked = false;
            boolean cancelled = false;

            org.bukkit.entity.Player player1 = player.getBukkitEntity();
            org.bukkit.util.Vector velocity = player1.getVelocity();

            PlayerVelocityEvent event = new PlayerVelocityEvent(player1, velocity.clone());
            if (!event.callEvent()) {
                cancelled = true;
            } else if (velocity != event.getVelocity() && !velocity.equals(event.getVelocity())) {
                player1.setVelocity(event.getVelocity());
            }
            if (cancelled) {
                continue;
            }
            ChunkMap.TrackedEntity trackedEntity = player.moonrise$getTrackedEntity();
            if (trackedEntity == null) {
                continue;
            }
            trackedEntity.broadcastAndSend(new ClientboundSetEntityMotionPacket(player));
        }
    }

    public static void onEntitiesTickEnd(ServerLevel world) {
        Future<TrackerCtx>[] task = world.trackerTask;
        if (task == null) {
            return;
        }
        for (Future<TrackerCtx> fut : task) {
            if (!fut.isDone()) {
                return;
            }
        }
        handle(world, task, false);
    }

    public static void onTickEnd(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            Future<TrackerCtx>[] task = world.trackerTask;
            if (task != null) {
                handle(world, task, false);
            }
        }
    }

    private static void handle(ServerLevel world, Future<TrackerCtx>[] futures, boolean flush) {
        try {
            TrackerCtx ctx = futures[0].get();
            for (int i = 1; i < futures.length; i++) {
                ctx.join(futures[i].get());
            }
            world.trackerTask = null;
            ctx.handle(flush);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
