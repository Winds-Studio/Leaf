package org.dreeam.leaf.async.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncTargetFinding;
import org.dreeam.leaf.util.queue.SpscIntQueue;

import java.util.OptionalInt;

public class AsyncGoalExecutor {

    protected static final Logger LOGGER = LogManager.getLogger("Leaf Async Goal");
    protected final SpscIntQueue queue;
    private final ServerLevel world;

    public AsyncGoalExecutor(ServerLevel world) {
        this.world = world;
        this.queue = new SpscIntQueue(AsyncTargetFinding.queueSize);
    }

    boolean wakeAll() {
        boolean success = false;
        while (true) {
            OptionalInt result = queue.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            success = true;
            wake(id);
        }
        return success;
    }

    public void tickMob(Mob mob) {
        if (!poll(mob)) {
            return;
        }
        int entityId = mob.getId();
        if (!this.queue.send(entityId)) {
            do {
                wake(entityId);
            } while (poll(mob));
        }
    }

    private void wake(int id) {
        Entity entity = this.world.getEntities().get(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return;
        }
        mob.goalSelector.ctx.wake(this.world);
        mob.targetSelector.ctx.wake(this.world);
    }

    private boolean poll(Mob mob) {
        try {
            mob.tickingTarget = true;
            boolean a = mob.targetSelector.poll();
            mob.tickingTarget = false;
            boolean b = mob.goalSelector.poll();
            return a || b;
        } catch (Exception e) {
            LOGGER.error("Exception while polling", e);
            return false;
        }
    }
}
