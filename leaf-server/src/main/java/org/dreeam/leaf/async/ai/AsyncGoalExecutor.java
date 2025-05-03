package org.dreeam.leaf.async.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncTargetFinding;
import org.dreeam.leaf.util.queue.SpscIntQueue;

import java.util.concurrent.locks.LockSupport;

public class AsyncGoalExecutor {

    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Goal");

    protected final SpscIntQueue queue;
    protected final SpscIntQueue wake;
    private final AsyncGoalThread thread;
    private final ServerLevel serverLevel;
    private boolean dirty = false;
    private long tickCount = 0L;
    private static final int SPIN_LIMIT = 100;

    public AsyncGoalExecutor(AsyncGoalThread thread, ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.queue = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.wake = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.thread = thread;
    }

    boolean wake(int id) {
        Entity entity = this.serverLevel.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return false;
        }
        mob.goalSelector.wake();
        mob.targetSelector.wake();
        return true;
    }

    public final void submit(int entityId) {
        if (!this.queue.send(entityId)) {
            int spinCount = 0;
            while (!this.queue.send(entityId)) {
                spinCount++;
                // Unpark the thread after some spinning to help clear the queue
                if (spinCount > SPIN_LIMIT) {
                    unpark();
                    spinCount = 0;
                }
                Thread.onSpinWait();
            }
        }
        dirty = true;
    }

    public final void unpark() {
        if (dirty) LockSupport.unpark(thread);
        dirty = false;
    }

    public final void midTick() {
        boolean didWork = false;
        while (true) {
            int id = this.wake.recv();
            if (id == Integer.MAX_VALUE) {
                break;
            }
            didWork = true;
            Entity entity = this.serverLevel.getEntities().get(id);
            if (entity == null || !entity.isAlive() || !(entity instanceof Mob mob)) {
                continue;
            }

            mob.tickingTarget = true;
            boolean a = mob.targetSelector.poll();
            mob.tickingTarget = false;
            boolean b = mob.goalSelector.poll();
            if (a || b) {
                submit(id);
            }
        }
        if (didWork || (tickCount & 15L) == 0L) unpark();
        tickCount += 1;
    }
}
