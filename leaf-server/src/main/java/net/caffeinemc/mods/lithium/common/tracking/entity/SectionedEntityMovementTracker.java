/*
 * This file is part of Lithium
 *
 * Lithium is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lithium is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lithium. If not, see <https://www.gnu.org/licenses/>.
 */

package net.caffeinemc.mods.lithium.common.tracking.entity;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityAccess;

import java.util.ArrayList;

public abstract class SectionedEntityMovementTracker<E extends EntityAccess> {
    final WorldSectionBox trackedWorldSections;
    final Object clazz;
    private final int trackedIndex;
    // Leaves start - Lithium Sleeping Block Entity
    ArrayList<MoonriseEntityMovementTrackerSection> sortedSections;
    // Moonrise owns section visibility and entity queries, so no separate sectionVisible array is needed.
    // Leaves end - Lithium Sleeping Block Entity
    private int timesRegistered;
    private final ArrayList<EntityMovementTrackerSection> sectionsNotListeningTo;
    private long maxChangeTime;
    private ReferenceOpenHashSet<SectionedEntityMovementListener> sectionedEntityMovementListeners;

    public SectionedEntityMovementTracker(WorldSectionBox interactionChunks, Object entityType) {
        this.clazz = entityType;
        this.trackedWorldSections = interactionChunks;
        this.trackedIndex = MovementTrackerHelper.getTrackerIndex(entityType);
        assert this.trackedIndex != -1;
        this.sectionedEntityMovementListeners = null;
        this.sectionsNotListeningTo = new ArrayList<>();
    }

    @Override
    public int hashCode() {
        return HashCommon.mix(this.trackedWorldSections.hashCode()) ^ HashCommon.mix(this.trackedIndex) ^ this.getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj.getClass() == this.getClass() &&
            this.clazz == ((SectionedEntityMovementTracker<?>) obj).clazz &&
            this.trackedWorldSections.equals(((SectionedEntityMovementTracker<?>) obj).trackedWorldSections);
    }

    /**
     * Method to quickly check whether any relevant entities moved inside the relevant entity sections after
     * the last interaction attempt.
     *
     * @param lastCheckedTime time of the last interaction attempt
     * @return whether any relevant entity moved in the tracked area
     */
    public boolean isUnchangedSince(long lastCheckedTime) {
        if (lastCheckedTime <= this.maxChangeTime) {
            return false;
        }
        if (!this.sectionsNotListeningTo.isEmpty()) {
            this.setChanged(this.listenToAllSectionsAndGetMaxChangeTime());
            return lastCheckedTime > this.maxChangeTime;
        }
        return true;
    }

    private long listenToAllSectionsAndGetMaxChangeTime() {
        long maxChangeTime = Long.MIN_VALUE;
        ArrayList<EntityMovementTrackerSection> notListeningTo = this.sectionsNotListeningTo;
        for (int i = notListeningTo.size() - 1; i >= 0; i--) {
            EntityMovementTrackerSection entityMovementTrackerSection = notListeningTo.remove(i);
            entityMovementTrackerSection.lithium$listenToMovementOnce(this, this.trackedIndex);
            maxChangeTime = Math.max(maxChangeTime, entityMovementTrackerSection.lithium$getChangeTime(this.trackedIndex));
        }
        return maxChangeTime;
    }

    // Leaves start - Lithium Sleeping Block Entity
    public void register(ServerLevel world) {
        assert world == this.trackedWorldSections.world();

        if (this.timesRegistered == 0) {
            WorldSectionBox trackedSections = this.trackedWorldSections;
            int size = trackedSections.numSections();
            assert size > 0;
            this.sortedSections = new ArrayList<>(size);
            int minSection = WorldUtil.getMinSection(world);
            int sectionCount = WorldUtil.getMaxSection(world) - minSection + 1;

            // WorldSectionBox upper coordinates are exclusive.
            for (int x = trackedSections.chunkX1(); x < trackedSections.chunkX2(); x++) {
                for (int z = trackedSections.chunkZ1(); z < trackedSections.chunkZ2(); z++) {
                    long chunkKey = CoordinateUtils.getChunkKey(x, z);
                    ChunkData chunkData = world.moonrise$requestChunkData(chunkKey);
                    if (chunkData.entityMovementTrackerSections == null) {
                        chunkData.entityMovementTrackerSections = new MoonriseEntityMovementTrackerSection[sectionCount];
                    }
                    ChunkEntitySlices slices = world.moonrise$getEntityLookup().getChunk(x, z);
                    boolean accessible = slices != null && slices.status.isOrAfter(FullChunkStatus.FULL);
                    for (int y = trackedSections.chunkY1(); y < trackedSections.chunkY2(); y++) {
                        int sectionIndex = y - minSection;
                        MoonriseEntityMovementTrackerSection section = chunkData.entityMovementTrackerSections[sectionIndex];
                        if (section == null) {
                            chunkData.entityMovementTrackerSections[sectionIndex] = section = new MoonriseEntityMovementTrackerSection(accessible);
                        }
                        this.sortedSections.add(section);
                        section.lithium$addListener(this);
                    }
                }
            }
            this.setChanged(world.getGameTime());
        }

        this.timesRegistered++;
    }

    public void unRegister(ServerLevel world) {
        assert world == this.trackedWorldSections.world();
        if (--this.timesRegistered > 0) {
            return;
        }
        assert this.timesRegistered == 0;
        ((ServerEntityLookup) world.moonrise$getEntityLookup()).entityMovementTrackers.deleteCanonical(this);

        WorldSectionBox trackedSections = this.trackedWorldSections;
        int minSection = WorldUtil.getMinSection(world);
        int index = this.sortedSections.size();
        for (int x = trackedSections.chunkX2() - 1; x >= trackedSections.chunkX1(); x--) {
            for (int z = trackedSections.chunkZ2() - 1; z >= trackedSections.chunkZ1(); z--) {
                long chunkKey = CoordinateUtils.getChunkKey(x, z);
                ChunkData chunkData = world.moonrise$getChunkData(chunkKey);
                for (int y = trackedSections.chunkY2() - 1; y >= trackedSections.chunkY1(); y--) {
                    MoonriseEntityMovementTrackerSection section = this.sortedSections.get(--index);
                    section.lithium$removeListener(this);
                    if (!this.sectionsNotListeningTo.remove(section)) {
                        section.lithium$removeListenToMovementOnce(this, this.trackedIndex);
                    }
                    if (section.isEmpty()) {
                        chunkData.entityMovementTrackerSections[y - minSection] = null;
                    }
                }
                world.moonrise$releaseChunkData(chunkKey);
            }
        }
        this.setChanged(world.getGameTime());
    }
    // Leaves end - Lithium Sleeping Block Entity

    // Leaves start - Lithium Sleeping Block Entity
    public void onSectionEnteredRange(EntityMovementTrackerSection section) {
        this.setChanged(this.trackedWorldSections.world().getGameTime());
        this.sectionsNotListeningTo.add(section);
        this.notifyAllListeners();
    }

    public void onSectionLeftRange(EntityMovementTrackerSection section) {
        this.setChanged(this.trackedWorldSections.world().getGameTime());
        if (!this.sectionsNotListeningTo.remove(section)) {
            section.lithium$removeListenToMovementOnce(this, this.trackedIndex);
            this.notifyAllListeners();
        }
    }

    // Leaves end - Lithium Sleeping Block Entity

    /**
     * Method that marks that new entities might have appeared or moved in the tracked chunk sections.
     */
    private void setChanged(long atTime) {
        if (atTime > this.maxChangeTime) {
            this.maxChangeTime = atTime;
        }
    }

    public void listenToEntityMovementOnce(SectionedEntityMovementListener listener) {
        if (this.sectionedEntityMovementListeners == null) {
            this.sectionedEntityMovementListeners = new ReferenceOpenHashSet<>();
        }
        this.sectionedEntityMovementListeners.add(listener);
        if (!this.sectionsNotListeningTo.isEmpty()) {
            this.setChanged(this.listenToAllSectionsAndGetMaxChangeTime());
        }
    }

    // Leaves start - Lithium Sleeping Block Entity
    public void emitEntityMovement(int classMask, EntityMovementTrackerSection section) {
        // Section listeners are already selected by notification type.
        this.notifyAllListeners();
        this.sectionsNotListeningTo.add(section);
    }
    // Leaves end - Lithium Sleeping Block Entity

    private void notifyAllListeners() {
        ReferenceOpenHashSet<SectionedEntityMovementListener> listeners = this.sectionedEntityMovementListeners;
        if (listeners != null && !listeners.isEmpty()) {
            for (SectionedEntityMovementListener listener : listeners) {
                listener.lithium$handleEntityMovement(this.clazz);
            }
            listeners.clear();
        }
    }
}
