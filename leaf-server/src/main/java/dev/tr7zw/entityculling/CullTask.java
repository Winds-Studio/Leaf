package dev.tr7zw.entityculling;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.Vec3d;
import dev.tr7zw.entityculling.versionless.access.Cullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.config.modules.misc.RaytraceTracker;

import java.util.concurrent.*;

public class CullTask implements Runnable {

    private static final String THREAD_PREFIX = "Leaf Raytrace Tracker";
    private volatile boolean scheduleNext = true;
    private volatile boolean isInit = false;

    private final OcclusionCullingInstance culling;
    private final Player checkTarget;

    private final int hitboxLimit;

    public long lastCheckedTime = 0;

    private final Vec3d lastPos = new Vec3d(0, 0, 0);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);

    private static final Executor backgroundWorker = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat(THREAD_PREFIX + " Thread - %d")
            .setDaemon(true)
            .setPriority(Thread.NORM_PRIORITY - 1)
            .build()
    );

    private final Executor worker;

    public CullTask(
        OcclusionCullingInstance culling,
        Player checkTarget,
        int hitboxLimit,
        long checkIntervalMs
    ) {
        this.culling = culling;
        this.checkTarget = checkTarget;
        this.hitboxLimit = hitboxLimit;
        this.worker = CompletableFuture.delayedExecutor(checkIntervalMs, TimeUnit.MILLISECONDS, backgroundWorker);
    }

    public void signalStop() {
        this.scheduleNext = false;
    }

    public void setup() {
        if (!this.isInit) {
            this.isInit = true;
        } else {
            return;
        }
        this.worker.execute(this);
    }

    @Override
    public synchronized void run() {
        try {
            if (this.checkTarget.tickCount > 10) {
                Vec3 cameraMC = this.checkTarget.getEyePosition(0);
                long start = System.currentTimeMillis();
                if (!(cameraMC.x == lastPos.x && cameraMC.y == lastPos.y && cameraMC.z == lastPos.z) || (start - lastCheckedTime) > 2500) {

                    lastPos.set(cameraMC.x, cameraMC.y, cameraMC.z);
                    culling.resetCache();

                    cullEntities(cameraMC, lastPos);

                    lastCheckedTime = System.currentTimeMillis() - start;
                }
            }
        } finally {
            if (this.scheduleNext) {
                this.worker.execute(this);
            }
        }
    }

    private void cullEntities(Vec3 cameraMC, Vec3d camera) {
        for (Entity entity : this.checkTarget.level().getEntities().getAll()) { // This one's safe here; moonrise returns an array for us to iterate
            if (!(entity instanceof Cullable cullable) || entity == this.checkTarget) {
                continue;
            }

            if (entity.getType().skipRaytraceCheck) {
                continue;
            }

            if (!cullable.isForcedVisible()) {
                if (entity.isCurrentlyGlowing() || isSkippableArmorstand(entity)) {
                    cullable.setCulled(false);
                    continue;
                }

                if (!entity.position().closerThan(cameraMC, RaytraceTracker.maxTraceDistance)) {
                    cullable.setCulled(false); // If your entity view distance is larger than tracingDistance just
                    // render it
                    continue;
                }

                AABB boundingBox = entity.getBoundingBox();
                if (boundingBox.getXsize() > hitboxLimit || boundingBox.getYsize() > hitboxLimit
                    || boundingBox.getZsize() > hitboxLimit) {
                    cullable.setCulled(false); // Too big to bother to cull
                    continue;
                }

                aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);

                boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);

                cullable.setCulled(!visible);
            }
        }
    }

    private boolean isSkippableArmorstand(Entity entity) {
        if (!RaytraceTracker.skipMarkerArmorStand) return false;
        return entity instanceof ArmorStand && entity.isInvisible();
    }
}
