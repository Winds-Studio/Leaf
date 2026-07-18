package net.caffeinemc.mods.lithium.common.tracking.entity;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSectionItemEntityMovementTracker extends ChunkSectionEntityMovementTracker {
    public static final Map<ChunkSectionIdentifier, ChunkSectionItemEntityMovementTracker> itemEntityMovementTrackerMap = new ConcurrentHashMap<>();

    public ChunkSectionItemEntityMovementTracker(long sectionKey, UUID levelId) {
        super(sectionKey, levelId);
    }

    @Override
    public void unregister() {
        this.userCount--;
        if (this.userCount <= 0) {
            itemEntityMovementTrackerMap.remove(identifier);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @NotNull List<ItemEntity> getEntities(@NotNull Level level, AABB boundingBox) {
        return level.getEntitiesOfClass((Class) ItemEntity.class, boundingBox, EntitySelector.ENTITY_STILL_ALIVE);
    }

    public static @NotNull List<ChunkSectionItemEntityMovementTracker> registerAt(ServerLevel world, AABB interactionArea) {
        WorldSectionBox worldSectionBox = WorldSectionBox.entityAccessBox(world, interactionArea);
        UUID levelId = world.uuid;

        if (worldSectionBox.chunkX1() == worldSectionBox.chunkX2() &&
            worldSectionBox.chunkY1() == worldSectionBox.chunkY2() &&
            worldSectionBox.chunkZ1() == worldSectionBox.chunkZ2()) {
            return Collections.singletonList(registerAt(
                CoordinateUtils.getChunkSectionKey(worldSectionBox.chunkX1(), worldSectionBox.chunkY1(), worldSectionBox.chunkZ1()),
                levelId
            ));
        }

        // Leaf start - pre-size ArrayList to avoid grow allocations
        int sizeX = worldSectionBox.chunkX2() - worldSectionBox.chunkX1() + 1;
        int sizeY = worldSectionBox.chunkY2() - worldSectionBox.chunkY1() + 1;
        int sizeZ = worldSectionBox.chunkZ2() - worldSectionBox.chunkZ1() + 1;
        List<ChunkSectionItemEntityMovementTracker> trackers = new ArrayList<>(sizeX * sizeY * sizeZ);
        // Leaf end - pre-size ArrayList to avoid grow allocations

        for (int x = worldSectionBox.chunkX1(); x <= worldSectionBox.chunkX2(); x++) {
            for (int y = worldSectionBox.chunkY1(); y <= worldSectionBox.chunkY2(); y++) {
                for (int z = worldSectionBox.chunkZ1(); z <= worldSectionBox.chunkZ2(); z++) {
                    trackers.add(registerAt(CoordinateUtils.getChunkSectionKey(x, y, z), levelId));
                }
            }
        }

        return trackers;
    }

    private static @NotNull ChunkSectionItemEntityMovementTracker registerAt(long key, UUID levelId) {
        ChunkSectionItemEntityMovementTracker tracker = itemEntityMovementTrackerMap.computeIfAbsent(
            new ChunkSectionIdentifier(key, levelId),
            k -> new ChunkSectionItemEntityMovementTracker(key, levelId)
        );
        tracker.register();
        return tracker;
    }
}
