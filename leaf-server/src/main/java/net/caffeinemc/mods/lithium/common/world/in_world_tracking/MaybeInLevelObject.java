package net.caffeinemc.mods.lithium.common.world.in_world_tracking;

import net.minecraft.world.level.Level;

public interface MaybeInLevelObject {

    boolean lithium$isInLevel();

    default void lithium$handleAddedToLevel(Level level) {
    }

    default void lithium$handleRemovedFromLevel(Level level) {
    }
}
