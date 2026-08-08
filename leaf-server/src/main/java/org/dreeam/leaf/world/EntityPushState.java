package org.dreeam.leaf.world;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;

public final class EntityPushState implements AbortableIterationConsumer<Entity> {
    @SuppressWarnings("NotNullFieldNotInitialized")
    private LivingEntity source;
    public int maxCramming;
    public int maxEntityCollisions;
    public final List<Entity> pushableEntities = new ReferenceArrayList<>();

    public int pushableCount;
    public int nonPassengerCount;
    public boolean foundAny;
    public boolean crammingCheckStarted;
    public boolean crammingResolved;
    public boolean crammingDamage;

    private boolean inUse;

    public EntityPushState() {
    }

    public void reset(
        final LivingEntity source,
        final int maxCramming,
        final int maxEntityCollisions
    ) {
        if (this.inUse) {
            throw new IllegalStateException("Re-entry of EntityPushState detected");
        }
        this.inUse = true;
        this.source = source;
        this.maxCramming = maxCramming;
        this.maxEntityCollisions = maxEntityCollisions;

        this.pushableEntities.clear();

        this.pushableCount = 0;
        this.nonPassengerCount = 0;

        this.foundAny = false;
        this.crammingCheckStarted = false;
        this.crammingResolved = maxCramming <= 0;
        this.crammingDamage = false;
    }

    public void release() {
        //noinspection DataFlowIssue
        this.source = null;
        this.pushableEntities.clear();
        this.inUse = false;
    }

    @Override
    public AbortableIterationConsumer.Continuation accept(final Entity entity) {
        this.foundAny = true;

        if (this.maxEntityCollisions > 0 && this.pushableEntities.size() < this.maxEntityCollisions) {
            this.pushableEntities.add(entity);
        }

        if (!this.crammingResolved) {
            if (this.pushableCount < this.maxCramming) {
                ++this.pushableCount;
            }
            if (!entity.isPassenger()) {
                ++this.nonPassengerCount;
            }

            if (this.pushableCount >= this.maxCramming) {
                if (!this.crammingCheckStarted) {
                    this.crammingCheckStarted = true;
                    if (this.source.getRandom().nextInt(4) != 0) {
                        this.crammingResolved = true;
                    }
                }

                if (!this.crammingResolved && this.nonPassengerCount >= this.maxCramming) {
                    this.crammingDamage = true;
                    this.crammingResolved = true;
                }
            }
        }

        final boolean collisionsResolved = this.maxEntityCollisions <= 0 || this.pushableEntities.size() >= this.maxEntityCollisions;
        return collisionsResolved && this.crammingResolved ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
    }
}
