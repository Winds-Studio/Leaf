package org.dreeam.leaf.async.ai;

import it.unimi.dsi.fastutil.ints.IntArrayList;
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

    protected static final Logger LOGGER = LogManager.getLogger("Leaf Async Goal");
    protected final SpscIntQueue queue;
    protected final SpscIntQueue wake;
    protected final IntArrayList submit;
    private final AsyncGoalThread thread;
    private final ServerLevel world;
    private long midTickCount = 0L;

    public AsyncGoalExecutor(AsyncGoalThread thread, ServerLevel world) {
        this.world = world;
        this.queue = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.wake = new SpscIntQueue(AsyncTargetFinding.queueSize);
        this.submit = new IntArrayList();
        this.thread = thread;
    }

    boolean wake(int id) {
        Entity entity = this.world.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return false;
        }
        mob.goalSelector.ctx.wake();
        mob.targetSelector.ctx.wake();
        return true;
    }

    public final void submit(int entityId) {
        this.submit.add(entityId);
    }

    public final void tick() {
        batchSubmit();
        LockSupport.unpark(thread);
    }

    private void batchSubmit() {
        if (submit.isEmpty()) {
            return;
        }
        int[] raw = submit.elements();
        int size = submit.size();
        for (int i = 0; i < size; i++) {
            int id = raw[i];
            if (poll(id) && !this.queue.send(id)) {
                do {
                    wake(id);
                } while (poll(id));
            }
        }
        this.submit.clear();
    }

    public final void midTick() {
        while (true) {
            OptionalInt result = this.wake.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            if (poll(id) && !this.queue.send(id)) {
                do {
                    wake(id);
                } while (poll(id));
            }
        }
        if (AsyncTargetFinding.threshold <= 0L || (midTickCount % AsyncTargetFinding.threshold) == 0L) {
            batchSubmit();
        }

        midTickCount += 1;
    }

    private boolean poll(int id) {
        Entity entity = this.world.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return false;
        }

        try {
            mob.tickingTarget = true;
            boolean a = mob.targetSelector.poll();
            mob.tickingTarget = false;
            boolean b = mob.goalSelector.poll();
            return a || b;
        } catch (Exception e) {
            LOGGER.error("Exception while polling", e);
            // retry
            return true;
        }
    }
}
