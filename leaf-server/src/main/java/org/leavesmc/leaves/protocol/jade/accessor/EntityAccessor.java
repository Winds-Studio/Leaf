package org.leavesmc.leaves.protocol.jade.accessor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

public interface EntityAccessor extends Accessor<EntityHitResult> {

    Entity getEntity();

    /**
     * For part entity like ender dragon's, getEntity() will return the parent entity.
     */
    Entity getRawEntity();

    @ApiStatus.NonExtendable
    interface Builder {
        Builder level(ServerLevel level);

        Builder player(Player player);

        default Builder hit(EntityHitResult hit) {
            return hit(() -> hit);
        }

        Builder hit(Supplier<EntityHitResult> hit);

        default Builder entity(Entity entity) {
            return entity(() -> entity);
        }

        Builder entity(Supplier<Entity> entity);

        Builder from(EntityAccessor accessor);

        EntityAccessor build();
    }
}