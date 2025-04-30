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
    final SpscIntQueue queue;
    final SpscIntQueue wake;
    private final AsyncGoalThread thread;
    private final ServerLevel serverLevel;
    private boolean dirty = false;
    private long tickCount = 0L;

    public AsyncGoalExecutor(AsyncGoalThread thread, ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        queue = new SpscIntQueue(AsyncTargetFinding.queueSize);
        wake = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.thread = thread;
    }

    boolean wake(int id) {
        Entity entity = this.serverLevel.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob m)) {
            return false;
        }
        m.goalSelector.wake();
        m.targetSelector.wake();
        return true;
    }

    public final void submit(int entityId) {
        if (!this.queue.send(entityId)) {
            LockSupport.unpark(thread);
            while (!this.queue.send(entityId)) {
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
        while (true) {
            int id = this.wake.recv();
            if (id == Integer.MAX_VALUE) {
                break;
            }
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
        if ((tickCount & 3L) == 0L) unpark();
        tickCount += 1;
    }
}

