package dev.tr7zw.entityculling;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.Vec3d;
import dev.tr7zw.entityculling.versionless.access.Cullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.config.modules.misc.RaytraceTracker;

import java.util.Set;
import java.util.concurrent.*;

public class CullTask implements Runnable {

    private static final String THREAD_PREFIX = "Leaf Raytrace Tracker";
    private volatile boolean scheduleNext = true;
    private volatile boolean isInit = false;

    private final OcclusionCullingInstance culling;
    private final Player checkTarget;

    private final int hitboxLimit;

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

    private final Set<Integer> culledEntities = ConcurrentHashMap.newKeySet();

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
                if (!(cameraMC.x == lastPos.x && cameraMC.y == lastPos.y && cameraMC.z == lastPos.z)) {
                    lastPos.set(cameraMC.x, cameraMC.y, cameraMC.z);
                    synchronized (culling) {
                        culling.resetCache();
                    }
                }
                cullEntities(cameraMC, lastPos);
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
            Player player = this.checkTarget;

            if (!cullable.isForcedVisible() || true) { // TODO
                if (entity.isCurrentlyGlowing() || isSkippableArmorstand(entity)) {
                    cullable.setCulled(false, player);
                    continue;
                }

                final double distanceSqr = entity.position().distanceToSqr(cameraMC);
                if (distanceSqr < RaytraceTracker.forceVisibleRadius * RaytraceTracker.forceVisibleRadius) {
                    cullable.setCulled(false, player);
                    continue;
                }

                if (distanceSqr >= RaytraceTracker.maxTraceDistance * RaytraceTracker.maxTraceDistance) {
                    cullable.setCulled(false, player); // If your entity view distance is larger than tracingDistance just
                    // render it
                    continue;
                }

                AABB boundingBox = entity.getBoundingBox();
                if (boundingBox.getXsize() > hitboxLimit || boundingBox.getYsize() > hitboxLimit
                    || boundingBox.getZsize() > hitboxLimit) {
                    cullable.setCulled(false, player); // Too big to bother to cull
                    continue;
                }

                aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);

                synchronized (culling) {
                    boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);

                    cullable.setCulled(!visible, player);
                }
            }
        }
    }

    public static void onBlockChange(Level level, BlockPos pos) {
        if (RaytraceTracker.enabled) {
            MinecraftServer server = level.getServer();
            if (server == null) { // tbh this cant be null
                return;
            }
            PlayerList playerList = server.getPlayerList();
            CompletableFuture.runAsync(() -> {
                for (Player player : playerList.realPlayers) {
                    CullTask cullTask = player.cullTask;
                    if (cullTask == null) continue;
                    if (player.level() == level) {
                        int posX = pos.getX();
                        int posY = pos.getY();
                        int posZ = pos.getZ();
                        BlockPos playerPos = player.blockPosition();
                        final int playerX = playerPos.getX(), playerY = playerPos.getY(), playerZ = playerPos.getZ();
                        if (Math.abs(posX - playerX) < RaytraceTracker.maxTraceDistance
                            && Math.abs(posY - playerY) < RaytraceTracker.maxTraceDistance
                            && Math.abs(posZ - playerZ) < RaytraceTracker.maxTraceDistance) {
                            synchronized (cullTask.culling) {
                                cullTask.culling.resetCache();
                            }
                        }
                    }
                }
            }, backgroundWorker);
        }
    }

    private boolean isSkippableArmorstand(Entity entity) {
        if (!RaytraceTracker.skipMarkerArmorStand) return false;
        return entity instanceof ArmorStand && entity.isInvisible();
    }

    public boolean isEntityCulled(Entity entity) {
        return culledEntities.contains(entity.getId());
    }

    public void setCulled(Entity entity, boolean value) {
        if (value) {
            culledEntities.add(entity.getId());
        } else {
            culledEntities.remove(entity.getId());
        }
    }
}
