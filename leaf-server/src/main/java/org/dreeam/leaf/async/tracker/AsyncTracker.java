package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.dreeam.leaf.async.ThreadPool;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;
import org.dreeam.leaf.util.TrackerSlice;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.*;

@NullMarked
public final class AsyncTracker {
    private static final String THREAD_NAME = "Leaf Async Tracker Thread";
    private static final int QUEUE = 1024;

    public static @Nullable ThreadPool TRACKER_EXECUTOR;

    private Future<TrackerCtx> @Nullable [] fut;

    private final ReferenceList<ChunkMap.TrackedEntity> trackersRef = new ReferenceList<>(new ChunkMap.TrackedEntity[0]); // TODO: Whether this should be kept, for avoiding a for loop
    private final Reference2ReferenceOpenHashMap<ChunkMap.TrackedEntity, TrackerInput> trackers = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceOpenHashMap<ChunkMap.TrackedEntity, TrackerInput> capture = new Reference2ReferenceOpenHashMap<>();

    public static void init() {
        if (TRACKER_EXECUTOR != null) {
            throw new IllegalStateException();
        }
        TRACKER_EXECUTOR = new ThreadPool(
            MultithreadedTracker.threads,
            QUEUE,
            THREAD_NAME
        );
    }

    public void addTracker(final ChunkMap.TrackedEntity tracker) {
        trackers.put(tracker, new TrackerInput(tracker.serverEntity.entity.trackingPosition(), Vec3.ZERO, false));
        trackersRef.add(tracker);
    }

    public void removeTracker(final ChunkMap.TrackedEntity tracker) {
        trackers.remove(tracker);
        trackersRef.remove(tracker);
    }

    public @Nullable TrackerInput get(final ChunkMap.@Nullable TrackedEntity tracker) {
        if (tracker == null) return null;
        TrackerInput i = capture.computeIfAbsent(tracker, _ -> new TrackerInput(Vec3.ZERO, Vec3.ZERO, false));
        i.trackingPosition = tracker.serverEntity.entity.trackingPosition();
        return i;
    }

    public void tick(final ServerLevel world) {
        var cap = capture.clone();
        capture.clear();
        handlePlayer(world);
        int len = trackers.size();
        if (len == 0) {
            return;
        }
        ChunkMap.TrackedEntity[] raw = new ChunkMap.TrackedEntity[len];
        System.arraycopy(trackersRef.getRawDataUnchecked(), 0, raw, 0, len);
        TrackerSlice slice = new TrackerSlice(raw);

        ThreadPool exec = Objects.requireNonNull(TRACKER_EXECUTOR);
        int min = MultithreadedTracker.minEntitiesPerTask;
        TrackerSlice[] slices = len <= exec.threadCount() * min ? slice.chunks(min) : slice.splitEvenly(exec.threadCount());
        @SuppressWarnings("unchecked")
        Future<TrackerCtx>[] futures = new Future[slices.length];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = exec.submitOrRun(new TrackerTask(world, slices[i], trackers, cap));
        }
        exec.unpark();
        this.fut = futures;
    }

    private static void handlePlayer(final ServerLevel world) {
        for (final ServerPlayer player : world.players()) {
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
            //noinspection ConstantValue
            if (trackedEntity == null) {
                continue;
            }
            trackedEntity.sendToTrackingPlayersAndSelf(new ClientboundSetEntityMotionPacket(player));
        }
    }

    public void onEntitiesTickEnd() {
        Future<TrackerCtx>[] task = this.fut;
        if (task == null) {
            return;
        }
        for (final Future<TrackerCtx> fut : task) {
            if (!fut.isDone()) {
                return;
            }
        }
        this.fut = null;
        handle(task);
    }

    public void onTickEnd() {
        Future<TrackerCtx>[] task = this.fut;
        this.fut = null;
        if (task == null) {
            return;
        }
        handle(task);
    }

    private static void handle(final Future<TrackerCtx>[] futures) {
        try {
            TrackerCtx ctx = futures[0].get();
            @SuppressWarnings("unchecked")
            Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>>[] packets = new Object2ObjectOpenHashMap[futures.length - 1];
            for (int i = 1; i < futures.length; i++) {
                packets[i - 1] = ctx.join(futures[i].get());
            }
            ctx.handle(packets);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
