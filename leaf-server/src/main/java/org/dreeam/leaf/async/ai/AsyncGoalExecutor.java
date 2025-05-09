package org.dreeam.leaf.async.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncTargetFinding;
import org.dreeam.leaf.util.queue.SpscIntQueue;

import java.util.OptionalInt;
import java.util.concurrent.locks.LockSupport;

public class AsyncGoalExecutor {

    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Goal");

    protected final SpscIntQueue queue;
    protected final SpscIntQueue wake;
    private final AsyncGoalThread thread;
    private final ServerLevel world;
    private boolean dirty = false;
    private long tickCount = 0L;

    public AsyncGoalExecutor(AsyncGoalThread thread, ServerLevel world) {
        this.world = world;
        this.queue = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.wake = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.thread = thread;
    }

    boolean wake(int id) {
        Entity entity = this.world.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return false;
        }
        mob.goalSelector.wake();
        mob.targetSelector.wake();
        return true;
    }

    public final void submit(int entityId) {
        dirty = true;
        if (!this.queue.send(entityId)) {
            unpark();
            do {
                wake(entityId);
            } while (poll(entityId));
        }
    }

    public final void unpark() {
        if (dirty) LockSupport.unpark(thread);
        dirty = false;
    }

    public final void midTick() {
        while (true) {
            OptionalInt result = this.wake.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            if (poll(id)) {
                submit(id);
            }
        }
        if ((tickCount % AsyncTargetFinding.threshold) == 0L) {
            unpark();
        }
        tickCount += 1;
    }

    private boolean poll(int id) {
        Entity entity = this.world.getEntities().get(id);
        if (entity == null || !entity.isAlive() || !(entity instanceof Mob mob)) {
            return false;
        }

        mob.tickingTarget = true;
        boolean a = mob.targetSelector.poll();
        mob.tickingTarget = false;
        boolean b = mob.goalSelector.poll();
        return a || b;
    }
}
