/*
 * Copyright (c) 2024 TelepathicGrunt
 * SPDX-License-Identifier: MIT
 *
 * Based on StructureLayoutOptimizer (https://github.com/TelepathicGrunt/StructureLayoutOptimizer)
 */
package org.dreeam.leaf.world.structure;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Carrier for a {@link BoxOctree} in the free-space slot of {@code JigsawPlacement}.
 * <p>
 * This is not a usable shape. Every user of the jigsaw free space must check for this type
 * and use {@link #boxOctree} instead of passing it to {@link net.minecraft.world.phys.shapes.Shapes}.
 */
public final class OctreeVoxelShape extends VoxelShape {
    public final BoxOctree boxOctree;

    public OctreeVoxelShape(final BoxOctree boxOctree) {
        super(BitSetDiscreteVoxelShape.withFilledBounds(0, 0, 0, 0, 0, 0, 0, 0, 0));
        this.boxOctree = boxOctree;
    }

    @Override
    public DoubleList getCoords(final Direction.Axis axis) {
        throw new UnsupportedOperationException("OctreeVoxelShape is only a jigsaw free space carrier");
    }
}
