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

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityAccess;

import java.util.List;

/**
 * Helps to track in which entity sections entities of a certain type moved, appeared or disappeared by providing int
 * masks for all Entity classes.
 * Helps to track the entities within a world and provide notifications to listeners when a tracked entity enters or leaves a
 * watched area. This removes the necessity to constantly poll the world for nearby entities each tick and generally
 * provides a sizable boost to performance of hoppers.
 */
public abstract class MovementTrackerHelper {
    // Leaves start - Lithium Sleeping Block Entity
    public static final List<Class<?>> MOVEMENT_NOTIFYING_ENTITY_CLASSES = List.of(ItemEntity.class, Container.class);
    public static volatile Reference2IntOpenHashMap<Class<? extends EntityAccess>> CLASS_2_NOTIFY_MASK;
    public static final int NUM_MOVEMENT_NOTIFYING_CLASSES = MOVEMENT_NOTIFYING_ENTITY_CLASSES.size();

    static {
        CLASS_2_NOTIFY_MASK = new Reference2IntOpenHashMap<>();
        CLASS_2_NOTIFY_MASK.defaultReturnValue(-1);
    }
    // Leaves end - Lithium Sleeping Block Entity

    public static int getNotificationMask(Entity entity) {
        int notificationMask = CLASS_2_NOTIFY_MASK.getInt(entity.getClass());
        if (notificationMask == -1) {
            notificationMask = calculateNotificationMask(entity);
        }
        return notificationMask;
    }

    private static int calculateNotificationMask(Entity entity) {
        int mask = 0;
        Class<? extends Entity> entityClass = entity.getClass();
        for (int i = 0; i < MOVEMENT_NOTIFYING_ENTITY_CLASSES.size(); i++) {
            Class<?> superclass = MOVEMENT_NOTIFYING_ENTITY_CLASSES.get(i);
            if (superclass.isAssignableFrom(entityClass)) {
                mask |= 1 << i;
            }
        }

        // Leaves - Lithium Sleeping Block Entity - Only hopper entity classes are tracked; collider class groups are not ported.

        //progress can be lost here, but it can only cost performance
        //copy on write followed by publication in volatile field guarantees visibility of the final state
        Reference2IntOpenHashMap<Class<? extends EntityAccess>> copy = CLASS_2_NOTIFY_MASK.clone();
        copy.put(entityClass, mask);
        CLASS_2_NOTIFY_MASK = copy;
        return mask;
    }

    // Leaves start - Lithium Sleeping Block Entity
    static int getTrackerIndex(Object classOrClassGroup) {
        return MOVEMENT_NOTIFYING_ENTITY_CLASSES.indexOf(classOrClassGroup);
    }
    // Leaves end - Lithium Sleeping Block Entity
}
