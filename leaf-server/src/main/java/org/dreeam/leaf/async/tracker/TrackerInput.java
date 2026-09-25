package org.dreeam.leaf.async.tracker;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TrackerInput {
    public Vec3 trackingPosition;
    public Vec3 predictedDelta;
    public boolean syncPosition;

    public TrackerInput(final Vec3 trackingPosition, final Vec3 predictedDelta, final boolean syncPosition) {
        this.trackingPosition = trackingPosition;
        this.predictedDelta = predictedDelta;
        this.syncPosition = syncPosition;
    }

    public void applyPredictedMovement(final Vec3 delta) {
        this.predictedDelta = this.predictedDelta.add(delta);
    }
}
