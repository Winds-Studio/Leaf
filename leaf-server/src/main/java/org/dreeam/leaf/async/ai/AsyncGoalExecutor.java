package org.dreeam.leaf.async.ai;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.PriorityQueues;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import it.unimi.dsi.fastutil.objects.ReferenceLists;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
    private long midTickCount = 0L;

    protected final MpmcIntQueue queue;
    protected final MpmcIntQueue wake;
    protected final IntArrayList submit;

    private final ReferenceList<Runnable> pathFindPostProcess;
    protected final MpmcIntQueue pathFindQueue;
    private final PriorityQueue<Path> brainPath = PriorityQueues.synchronize(new ObjectArrayFIFOQueue<>());

    public AsyncGoalExecutor(ServerLevel world) {
        this.world = world;
        this.queue = new MpmcIntQueue(AsyncTargetFinding.queueSize);
        this.wake = new MpmcIntQueue(AsyncTargetFinding.queueSize);
        this.submit = new IntArrayList();
        this.pathFindQueue = new MpmcIntQueue(AsyncTargetFinding.queueSize);
        this.pathFindPostProcess = ReferenceLists.synchronize(new ReferenceArrayList<>());
    }

    public final void submitBrainPath(Path path) {
        brainPath.enqueue(path);
    }

    public final void submitFindPath(int id) {
        if (!pathFindQueue.send(id)) {
            wakePathFind(id);
            runPathFindPostProcess();
        }
    }

    public final void submit(int entityId) {
        this.submit.add(entityId);
    }

    public final void tick() {
        batchSubmit();
        runPathFindPostProcess();
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
        runPathFindPostProcess();
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

    private void runPathFindPostProcess() {
        synchronized (pathFindPostProcess) {
            for (final Runnable findPostProcess : this.pathFindPostProcess) {
                findPostProcess.run();
            }
            pathFindPostProcess.clear();
        }
    }

    private boolean poll(int id) {
        Entity entity = this.world.getEntity(id);
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

    boolean runAll() {
        boolean success = false;
        while (true) {
            OptionalInt result = queue.recv();
            if (result.isEmpty()) {
                break;
            }
            int id = result.getAsInt();
            success = true;
            if (wake(id)) {
                while (!wake.send(id)) {
                    Thread.onSpinWait();
                }
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

    private boolean wake(int id) {
        Entity entity = this.world.getEntity(id);
        if (entity == null || entity.isRemoved() || !(entity instanceof Mob mob)) {
            return false;
        }
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
                    this.pathFindPostProcess.add((Runnable) o);
                }
            }
        }
    }

    private void wakeBrain(Path path) {
        if (path instanceof AsyncPath asyncPath && asyncPath.wake() instanceof List<?> list) {
            for (Object o : list) {
                this.pathFindPostProcess.add((Runnable) o);
            }
        }
    }
}
