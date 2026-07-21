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

package net.caffeinemc.mods.lithium.common.util;

import net.minecraft.core.Direction;

/**
 * Pre-initialized constants to avoid unnecessary allocations.
 */
public final class DirectionConstants {
    private DirectionConstants() {
    }

    public static final Direction[] ALL = Direction.values();
    public static final Direction[] VERTICAL = {Direction.DOWN, Direction.UP};
    public static final Direction[] HORIZONTAL = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
    public static final byte[] HORIZONTAL_OPPOSITE_INDICES = {1, 0, 3, 2};
}
