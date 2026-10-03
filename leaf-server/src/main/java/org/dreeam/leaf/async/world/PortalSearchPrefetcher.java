package org.dreeam.leaf.async.world;

import ca.spottedleaf.concurrentutil.util.Priority;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.dreeam.leaf.config.modules.async.AsyncPortalSearch;

public final class PortalSearchPrefetcher {
    private static final int MAX_PREFETCH_CHUNK_RADIUS = 4;

    private PortalSearchPrefetcher() { }

    public static void onPortalEnter(final Entity entity, final Portal portal) {
        if (!AsyncPortalSearch.enabled) return;
        if (!(portal instanceof NetherPortalBlock)) return;
        if (!(entity.level() instanceof ServerLevel currentLevel)) return;
        if (portal.getPortalTransitionTime(currentLevel, entity) < 2) return;

        final ResourceKey<Level> newDimension = currentLevel.getTypeKey() == LevelStem.NETHER ? Level.OVERWORLD : Level.NETHER;
        final ServerLevel newLevel = currentLevel.getServer().getLevel(newDimension);
        if (newLevel == null) return;

        final boolean toNether = newLevel.getTypeKey() == LevelStem.NETHER;
        final double teleportationScale = DimensionType.getTeleportationScale(currentLevel.dimensionType(), newLevel.dimensionType());
        final BlockPos approximateExitPos = newLevel.getWorldBorder().clampToBounds(entity.getX() * teleportationScale, entity.getY(), entity.getZ() * teleportationScale);

        int searchRadius = newLevel.paperConfig().environment.portalSearchRadius;
        if (currentLevel.paperConfig().environment.portalSearchVanillaDimensionScaling && toNether) {
            searchRadius = (int) (searchRadius / newLevel.dimensionType().coordinateScale());
        }

        final int chunkRadius = Math.min(MAX_PREFETCH_CHUNK_RADIUS, (searchRadius >> 4) + 1);
        final int centerX = approximateExitPos.getX() >> 4;
        final int centerZ = approximateExitPos.getZ() >> 4;
        final var scheduler = newLevel.moonrise$getChunkTaskScheduler();

        for (int dz = -chunkRadius; dz <= chunkRadius; ++dz) {
            for (int dx = -chunkRadius; dx <= chunkRadius; ++dx) {
                scheduler.scheduleChunkLoad(centerX + dx, centerZ + dz, false, ChunkStatus.EMPTY, true, Priority.LOW, null);
            }
        }
    }
}
