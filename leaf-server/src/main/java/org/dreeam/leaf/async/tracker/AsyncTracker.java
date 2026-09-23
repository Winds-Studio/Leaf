package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.dreeam.leaf.async.FixedThreadExecutor;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;
import org.dreeam.leaf.util.TrackerSlice;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.*;

@NullMarked
public final class AsyncTracker {
    private static final String THREAD_NAME = "Leaf Async Tracker Thread";
    public static final boolean ENABLED = MultithreadedTracker.enabled;
    public static final int QUEUE = 1024;
    public static final int MIN_CHUNK = 16;
    public static final int THREADS = MultithreadedTracker.threads;
    public static final @Nullable FixedThreadExecutor TRACKER_EXECUTOR = ENABLED ? new FixedThreadExecutor(
        THREADS,
        QUEUE,
        THREAD_NAME
    ) : null;

    private Future<TrackerCtx> @Nullable [] fut;
    private final TrackerCtx local;
    private final ReferenceList<ChunkMap.TrackedEntity> trackers = new ReferenceList<>(new ChunkMap.TrackedEntity[0]); // TODO: Whether this should be kept, for avoiding a for loop
    private long writeBatch;

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

    public void addTracker(final ChunkMap.TrackedEntity tracker) {
        this.trackers.add(tracker);
    }

    public void removeTracker(final ChunkMap.TrackedEntity tracker) {
        this.trackers.remove(tracker);
    }

    public long writeBatch() {
        return this.writeBatch;
    }

    public void tick(ServerLevel world) {
        handlePlayer(world);
        int len = this.trackers.size();
        if (len == 0) {
            return;
        }
        long batch = this.writeBatch++;
        ChunkMap.TrackedEntity[] trackers = new ChunkMap.TrackedEntity[len];
        System.arraycopy(this.trackers.getRawDataUnchecked(), 0, trackers, 0, len);
        TrackerSlice slice = new TrackerSlice(trackers);
        TrackerSlice[] slices = len <= THREADS * MIN_CHUNK ? slice.chunks(MIN_CHUNK) : slice.splitEvenly(THREADS);
        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slices.length];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = TRACKER_EXECUTOR.submitOrRun(new TrackerTask(world, slices[i], batch));
        }
        TRACKER_EXECUTOR.unpark();
        this.fut = futures;
    }

    private static void handlePlayer(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            player.updateDataBeforeSync();

            if (!player.syncVelocity) {
                continue;
            }
            player.syncVelocity = false;
            boolean cancelled = false;

            org.bukkit.entity.Player craftPlayer = player.getBukkitEntity();
            org.bukkit.util.Vector velocity = craftPlayer.getVelocity();

            PlayerVelocityEvent event = new PlayerVelocityEvent(craftPlayer, velocity.clone());
            if (!event.callEvent()) {
                cancelled = true;
            } else if (velocity != event.getVelocity() && !velocity.equals(event.getVelocity())) {
                craftPlayer.setVelocity(event.getVelocity());
            }
            if (cancelled) {
                continue;
            }
            ChunkMap.TrackedEntity trackedEntity = player.moonrise$getTrackedEntity();
            if (trackedEntity == null) {
                continue;
            }
            trackedEntity.sendToTrackingPlayersAndSelf(new ClientboundSetEntityMotionPacket(player));
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
    }

    public void onTickEnd() {
        Future<TrackerCtx>[] task = this.fut;
        TrackerCtx local = this.local;
        this.fut = null;
        handle(task, local);
        local.reset();
    }

    private static void handle(Future<TrackerCtx> @Nullable [] futures, TrackerCtx local) {
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
