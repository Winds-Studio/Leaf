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

package net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.lithium.common.util.DirectionConstants;

public class ComparatorTracking {

    public static void notifyNearbyBlockEntitiesAboutNewComparator(Level world, BlockPos pos) {
        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();
        for (Direction searchDirection : DirectionConstants.HORIZONTAL) {
            for (int searchOffset = 1; searchOffset <= 2; searchOffset++) {
                searchPos.set(pos);
                searchPos.move(searchDirection, searchOffset);
                BlockState blockState = world.getBlockState(searchPos);
                if (blockState.getBlock() instanceof EntityBlock) {
                    BlockEntity blockEntity = world.lithium$getLoadedExistingBlockEntity(searchPos);
                    if (blockEntity instanceof Container) {
                        blockEntity.lithium$onComparatorAdded(searchDirection, searchOffset);
                    }
                }
            }
        }
    }

    public static boolean findNearbyComparators(Level world, BlockPos pos) {
        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();
        for (Direction searchDirection : DirectionConstants.HORIZONTAL) {
            for (int searchOffset = 1; searchOffset <= 2; searchOffset++) {
                searchPos.set(pos);
                searchPos.move(searchDirection, searchOffset);
                BlockState blockState = world.getBlockState(searchPos);
                if (blockState.is(Blocks.COMPARATOR)) {
                    return true;
                }
            }
        }
        return false;
    }
}
