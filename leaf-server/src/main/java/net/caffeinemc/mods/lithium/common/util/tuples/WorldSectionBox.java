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

package net.caffeinemc.mods.lithium.common.util.tuples;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

//Y values use coordinates, not indices (y=0 -> chunkY=0)
//upper bounds are EXCLUSIVE
public record WorldSectionBox(Level world, int chunkX1, int chunkY1, int chunkZ1, int chunkX2, int chunkY2,
                              int chunkZ2) {
    // Leaves start - Lithium Sleeping Block Entity
    public static WorldSectionBox entityAccessBox(Level world, AABB box) {
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0D);
        int minSection = WorldUtil.getMinSection(world);
        int maxSection = WorldUtil.getMaxSection(world);
        int minY = Mth.clamp(SectionPos.posToSectionCoord(box.minY - 4.0D), minSection, maxSection);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0D);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0D) + 1;
        int maxY = Mth.clamp(SectionPos.posToSectionCoord(box.maxY), minSection, maxSection) + 1;
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0D) + 1;
        return new WorldSectionBox(world, minX, minY, minZ, maxX, maxY, maxZ);
    }
    // Leaves end - Lithium Sleeping Block Entity

    public int numSections() {
        return (this.chunkX2 - this.chunkX1) * (this.chunkY2 - this.chunkY1) * (this.chunkZ2 - this.chunkZ1);
    }
}
