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

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SectionedInventoryEntityMovementTracker<S> extends SectionedEntityMovementTracker<Entity> {
    public SectionedInventoryEntityMovementTracker(WorldSectionBox entityAccessBox, Class<S> clazz) {
        super(entityAccessBox, clazz);
    }

    public static <S> SectionedInventoryEntityMovementTracker<S> registerAt(ServerLevel world, AABB interactionArea, Class<S> clazz) {
        WorldSectionBox worldSectionBox = WorldSectionBox.entityAccessBox(world, interactionArea);
        SectionedInventoryEntityMovementTracker<S> tracker = new SectionedInventoryEntityMovementTracker<>(worldSectionBox, clazz);
        tracker = ((ServerEntityLookup) world.moonrise$getEntityLookup()).entityMovementTrackers.getCanonical(tracker); // Leaves - Lithium Sleeping Block Entity
        tracker.register(world);
        return tracker;
    }

    // Leaves start - Lithium Sleeping Block Entity
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<S> getEntities(AABB box) {
        return this.trackedWorldSections.world().getEntitiesOfClass((Class) this.clazz, box, EntitySelector.CONTAINER_ENTITY_SELECTOR);
    }
    // Leaves end - Lithium Sleeping Block Entity
}
