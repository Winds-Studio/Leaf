package org.dreeam.leaf.async.tracker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationTracker;
import net.minecraft.world.phys.Vec3;

// Thanks to Mojang's interpolation rewrite
public final class TrackerInput {
    private final AsyncTracker owner;
    private final boolean trackPredictedMovement;
    private final Frame first = new Frame();
    private final Frame second = new Frame();
    private volatile Frame latest;

    private long batch;
    private long consumedBatch = -1;
    private Vec3 position;
    private Vec3 pendingDelta = Vec3.ZERO;
    private boolean pendingSyncPosition;

    public TrackerInput(final AsyncTracker owner, final Entity entity, final InterpolationTracker source) {
        this.owner = owner;
        this.trackPredictedMovement = source != InterpolationTracker.NO_OP;
        this.first.batch = owner.writeBatch();
        this.first.position = entity.trackingPosition();
        this.first.predictedDelta = source.leaf$takePredictedMovement();
        this.first.syncPosition = entity.syncPosition;
        entity.syncPosition = false;
        this.latest = this.first;
    }

    private Frame writable() {
        Frame frame = this.latest;
        long writeBatch = this.owner.writeBatch();
        if (frame.batch != writeBatch) {
            Frame next = frame == this.first ? this.second : this.first;
            next.batch = writeBatch;
            next.position = frame.position;
            next.predictedDelta = Vec3.ZERO;
            next.syncPosition = false;
            this.latest = next;
            frame = next;
        }
        return frame;
    }

    public void setPosition(final Vec3 position) {
        this.writable().position = position;
    }

    public void addMovement(final Vec3 movement) {
        if (this.trackPredictedMovement) {
            Frame frame = this.writable();
            frame.predictedDelta = frame.predictedDelta.add(movement);
        }
    }

    public void markSyncPosition() {
        this.writable().syncPosition = true;
    }

    public void collect(final long batch) {
        this.batch = batch;
        Frame frame = this.latest;
        if (frame.batch > batch) {
            frame = frame == this.first ? this.second : this.first;
        }
        this.position = frame.position;
        if (frame.batch != this.consumedBatch) {
            this.accumulate(frame);
            this.consumedBatch = frame.batch;
        }
    }

    public void collectCurrent() {
        Frame frame = this.latest;
        this.position = frame.position;
        if (frame.batch > this.batch) {
            this.accumulate(frame);
            frame.predictedDelta = Vec3.ZERO;
            frame.syncPosition = false;
        }
    }

    private void accumulate(final Frame frame) {
        if (frame.predictedDelta != Vec3.ZERO) {
            this.pendingDelta = this.pendingDelta == Vec3.ZERO
                ? frame.predictedDelta : this.pendingDelta.add(frame.predictedDelta);
        }
        this.pendingSyncPosition |= frame.syncPosition;
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 takePredictedMovement() {
        Vec3 delta = this.pendingDelta;
        this.pendingDelta = Vec3.ZERO;
        return delta;
    }

    public boolean takeSyncPosition() {
        boolean syncPosition = this.pendingSyncPosition;
        this.pendingSyncPosition = false;
        return syncPosition;
    }

    private static final class Frame {
        private long batch = -1;
        private Vec3 position;
        private Vec3 predictedDelta = Vec3.ZERO;
        private boolean syncPosition;
    }
}
