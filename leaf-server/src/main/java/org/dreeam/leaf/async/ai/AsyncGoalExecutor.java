package org.dreeam.leaf.async.ai;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.PriorityQueues;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import it.unimi.dsi.fastutil.objects.ReferenceLists;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.async.path.AsyncPath;
import org.dreeam.leaf.config.modules.async.AsyncTargetFinding;
import org.dreeam.leaf.util.queue.MpmcIntQueue;

import java.util.List;
import java.util.OptionalInt;

public class AsyncGoalExecutor {

    protected static final Logger LOGGER = LogManager.getLogger("Leaf Async AI");
    private final ServerLevel world;

    protected final MpmcIntQueue queue;
    private final ReferenceList<Runnable> postProcess = ReferenceLists.synchronize(new ReferenceArrayList<>());
    protected final MpmcIntQueue pathFindQueue;
    private final PriorityQueue<Path> brainPath = PriorityQueues.synchronize(new ObjectArrayFIFOQueue<>());
    public final ReferenceList<Goal> tickGoal = ReferenceLists.synchronize(new ReferenceArrayList<>());
    public final PriorityQueue<Goal> createPath = PriorityQueues.synchronize(new ObjectArrayFIFOQueue<>());

    public AsyncGoalExecutor(ServerLevel world) {
        this.world = world;
        this.queue = new MpmcIntQueue(AsyncTargetFinding.queueSize);
        this.pathFindQueue = new MpmcIntQueue(AsyncTargetFinding.queueSize);
    }

    public final void tickGoal(Goal goal) {
        if (goal instanceof AsyncGoal asyncGoal && asyncGoal.tickAsync()) {
            goal.tick();
        } else {
            tickGoal.add(goal);
        }
    }

    public final void submitBrainPath(Path path) {
        brainPath.enqueue(path);
    }

    public final void submitFindPath(int id) {
        if (!pathFindQueue.send(id)) {
            wakePathFind(id);
        }
    }

    public final void submit(int entityId) {
        if (!this.queue.send(entityId)) {
            Entity entity = this.world.getEntity(entityId);
            if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
                return;
            }
            do {
                wake(mob);
            } while (poll(mob));
        }
    }

    public final void post(Runnable r) {
        this.postProcess.add(r);
    }

    public final void tick() {
        midTick();
    }

    private void execPathFindPostProcess() {
        synchronized (postProcess) {
            for (final Runnable findPostProcess : postProcess) {
                findPostProcess.run();
            }
            postProcess.clear();
        }
    }
    public final void midTick() {
        execPathFindPostProcess();
        synchronized (tickGoal) {
            for (final Goal goal : tickGoal) {
                goal.tick();
            }
            tickGoal.clear();
        }
    }

    private boolean poll(Mob mob) {
        try {
            mob.tickingTarget = true;
            boolean a = mob.targetSelector.poll(this);
            mob.tickingTarget = false;
            boolean b = mob.goalSelector.poll(this);
            return a || b;
        } catch (Exception e) {
            LOGGER.error("Exception while polling", e);
            // retry
            return true;
        }
    }

    boolean runAll() {
        boolean success = false;
        while (true) {
            OptionalInt result = queue.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            success = true;
            Entity entity = this.world.getEntity(id);
            if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
                continue;
            }
            while (poll(mob) && wake(mob)) {
            }
        }
        while (true) {
            OptionalInt result = pathFindQueue.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            success = true;
            wakePathFind(id);
        }
        while (true) {
            Path path;
            synchronized (brainPath) {
                if (brainPath.isEmpty()) {
                    break;
                } else {
                    path = brainPath.dequeue();
                }
            }
            wakeBrain(path);
            success = true;
        }
        return success;
    }

    private boolean wake(Mob mob) {
        mob.goalSelector.ctx.wake();
        mob.targetSelector.ctx.wake();
        return true;
    }

    private void wakePathFind(int id) {
        Entity entity = this.world.getEntity(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getNavigationUnchecked().getPath() instanceof AsyncPath asyncPath) {
            if (asyncPath.wake() instanceof List<?> list) {
                for (Object o : list) {
                    this.postProcess.add((Runnable) o);
                }
            }
        }
    }

    private void wakeBrain(Path path) {
        if (path instanceof AsyncPath asyncPath && asyncPath.wake() instanceof List<?> list) {
            for (Object o : list) {
                this.postProcess.add((Runnable) o);
            }
        }
    }
}
