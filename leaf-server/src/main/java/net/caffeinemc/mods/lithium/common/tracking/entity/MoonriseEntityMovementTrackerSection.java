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

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.entity.EntityAccess;

import java.util.ArrayList;

// Leaves - Lithium Sleeping Block Entity - Moonrise counterpart of util.entity_movement_tracking.EntitySectionMixin
public final class MoonriseEntityMovementTrackerSection implements EntityMovementTrackerSection {
    private boolean accessible; // Leaves - Lithium Sleeping Block Entity
    private final ReferenceOpenHashSet<SectionedEntityMovementTracker<?>> sectionVisibilityListeners = new ReferenceOpenHashSet<>(0);
    @SuppressWarnings("unchecked")
    private final ArrayList<SectionedEntityMovementTracker<?>>[] entityMovementListenersByType = new ArrayList[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];
    private final long[] lastEntityMovementByType = new long[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];

    // Leaves start - Lithium Sleeping Block Entity
    public MoonriseEntityMovementTrackerSection(boolean accessible) {
        this.accessible = accessible;
    }
    // Leaves end - Lithium Sleeping Block Entity

    @Override
    public void lithium$addListener(SectionedEntityMovementTracker<?> listener) {
        this.sectionVisibilityListeners.add(listener);
        if (this.accessible) { // Leaves - Lithium Sleeping Block Entity
            listener.onSectionEnteredRange(this);
        }
    }

    // Leaves start - Lithium Sleeping Block Entity
    @Override
    public void lithium$removeListener(SectionedEntityMovementTracker<?> listener) {
        this.sectionVisibilityListeners.remove(listener);
        if (this.accessible) {
            listener.onSectionLeftRange(this);
        }
    }

    public boolean isEmpty() {
        return this.sectionVisibilityListeners.isEmpty();
    }

    public void lithium$updateStatus(boolean accessible) {
        if (this.accessible != accessible) {
            for (SectionedEntityMovementTracker<?> listener : this.sectionVisibilityListeners) {
                if (accessible) {
                    listener.onSectionEnteredRange(this);
                } else {
                    listener.onSectionLeftRange(this);
                }
            }
            this.accessible = accessible;
        }
    }
    // Leaves end - Lithium Sleeping Block Entity

    @Override
    public void lithium$trackEntityMovement(int notificationMask, long time) {
        long[] lastEntityMovementByType = this.lastEntityMovementByType;
        int size = lastEntityMovementByType.length;
        int mask;
        for (int entityClassIndex = Integer.numberOfTrailingZeros(notificationMask); entityClassIndex < size; ) {
            lastEntityMovementByType[entityClassIndex] = time;

            ArrayList<SectionedEntityMovementTracker<?>> entityMovementListeners = this.entityMovementListenersByType[entityClassIndex];
            if (entityMovementListeners != null) {
                for (int listIndex = entityMovementListeners.size() - 1; listIndex >= 0; listIndex--) {
                    SectionedEntityMovementTracker<?> sectionedEntityMovementTracker = entityMovementListeners.remove(listIndex);
                    sectionedEntityMovementTracker.emitEntityMovement(notificationMask, this);
                }
            }

            mask = 0xffff_fffe << entityClassIndex;
            entityClassIndex = Integer.numberOfTrailingZeros(notificationMask & mask);
        }
    }

    @Override
    public long lithium$getChangeTime(int trackedClass) {
        return this.lastEntityMovementByType[trackedClass];
    }

    @Override
    public <S, E extends EntityAccess> void lithium$listenToMovementOnce(SectionedEntityMovementTracker<E> listener, int trackedClass) {
        if (this.entityMovementListenersByType[trackedClass] == null) {
            this.entityMovementListenersByType[trackedClass] = new ArrayList<>();
        }
        this.entityMovementListenersByType[trackedClass].add(listener);
    }

    @Override
    public <S, E extends EntityAccess> void lithium$removeListenToMovementOnce(SectionedEntityMovementTracker<E> listener, int trackedClass) {
        if (this.entityMovementListenersByType[trackedClass] != null) {
            this.entityMovementListenersByType[trackedClass].remove(listener);
        }
    }
}
