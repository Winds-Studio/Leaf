package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
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

    private Future<TrackerCtx>[] fut;
    private final TrackerCtx local;

    public AsyncTracker(ServerLevel world) {
        this.local = new TrackerCtx(world);
    }

    public static void init() {
        if (TRACKER_EXECUTOR == null || !ENABLED) {
            throw new IllegalStateException();
        }
    }

    public TrackerCtx ctx() {
        return this.local;
    }

    public void tick(ServerLevel world) {
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
        this.fut = futures;
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

    public void onEntitiesTickEnd() {
        Future<TrackerCtx>[] task = this.fut;
        TrackerCtx local = this.local;
        if (task == null) {
            return;
        }
        for (Future<TrackerCtx> fut : task) {
            if (!fut.isDone()) {
                return;
            }
        }
        this.fut = null;
        handle(task, local);
        local.reset();
        // for (ServerPlayer player : world.players()) {
        //     player.connection.connection.flushChannel();
        // }
    }

    public void onTickEnd() {
        Future<TrackerCtx>[] task = this.fut;
        TrackerCtx local = this.local;
        this.fut = null;
        handle(task, local);
        local.reset();
    }

    private static void handle(Future<TrackerCtx>[] futures, TrackerCtx local) {
        try {
            if (futures == null) {
                local.handle(new Object2ObjectOpenHashMap[0]);
            } else {
                TrackerCtx ctx = futures[0].get();
                @SuppressWarnings("unchecked")
                Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>>[] packets = new Object2ObjectOpenHashMap[futures.length];
                packets[futures.length - 1] = ctx.join(local);
                for (int i = 1; i < futures.length; i++) {
                    packets[i - 1] = ctx.join(futures[i].get());
                }
                ctx.handle(packets);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
