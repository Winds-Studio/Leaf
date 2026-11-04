/*
 * Copyright (c) 2024 TelepathicGrunt
 * SPDX-License-Identifier: MIT
 *
 * Based on StructureLayoutOptimizer (https://github.com/TelepathicGrunt/StructureLayoutOptimizer)
 */
package org.dreeam.leaf.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the occupied boxes of a jigsaw structure inside its boundary.
 * <p>
 * Replaces the vanilla free-space {@link net.minecraft.world.phys.shapes.VoxelShape}, which gets more expensive
 * to join and test with every placed piece.
 */
public final class BoxOctree {

    private static final int SUBDIVIDE_THRESHOLD = 10;
    private static final int MAXIMUM_DEPTH = 3;

    private final AABB boundary;
    private final Vec3i size;
    private final int depth;
    private final List<AABB> innerBoxes = new ArrayList<>();
    private final List<BoxOctree> childrenOctants = new ArrayList<>();

    public BoxOctree(final AABB boundary) {
        this(boundary, 0);
    }

    private BoxOctree(final AABB boundary, final int parentDepth) {
        this.boundary = boundary;
        this.size = new Vec3i(roundAwayFromZero(boundary.getXsize()), roundAwayFromZero(boundary.getYsize()), roundAwayFromZero(boundary.getZsize()));
        this.depth = parentDepth + 1;
    }

    private static int roundAwayFromZero(final double value) {
        return (value >= 0) ? (int) Math.ceil(value) : (int) Math.floor(value);
    }

    private void subdivide() {
        if (!this.childrenOctants.isEmpty()) {
            throw new UnsupportedOperationException("Tried to subdivide when there are already children octants.");
        }

        final int halfXSize = this.size.getX() / 2;
        final int halfYSize = this.size.getY() / 2;
        final int halfZSize = this.size.getZ() / 2;
        final AABB b = this.boundary;

        // Lower Left Back Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX, b.minY, b.minZ,
            b.minX + halfXSize, b.minY + halfYSize, b.minZ + halfZSize),
            this.depth));

        // Lower Left Front Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX, b.minY, b.minZ + halfZSize,
            b.minX + halfXSize, b.minY + halfYSize, b.maxZ),
            this.depth));

        // Lower Right Back Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX + halfXSize, b.minY, b.minZ,
            b.maxX, b.minY + halfYSize, b.minZ + halfZSize),
            this.depth));

        // Lower Right Front Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX + halfXSize, b.minY, b.minZ + halfZSize,
            b.maxX, b.minY + halfYSize, b.maxZ),
            this.depth));

        // Upper Left Back Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX, b.minY + halfYSize, b.minZ,
            b.minX + halfXSize, b.maxY, b.minZ + halfZSize),
            this.depth));

        // Upper Left Front Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX, b.minY + halfYSize, b.minZ + halfZSize,
            b.minX + halfXSize, b.maxY, b.maxZ),
            this.depth));

        // Upper Right Back Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX + halfXSize, b.minY + halfYSize, b.minZ,
            b.maxX, b.maxY, b.minZ + halfZSize),
            this.depth));

        // Upper Right Front Corner
        this.childrenOctants.add(new BoxOctree(new AABB(
            b.minX + halfXSize, b.minY + halfYSize, b.minZ + halfZSize,
            b.maxX, b.maxY, b.maxZ),
            this.depth));

        for (int i = 0, size = this.innerBoxes.size(); i < size; i++) {
            final AABB parentInnerBox = this.innerBoxes.get(i);
            for (int j = 0, octants = this.childrenOctants.size(); j < octants; j++) {
                final BoxOctree octree = this.childrenOctants.get(j);
                if (octree.boundaryIntersects(parentInnerBox)) {
                    octree.addBox(parentInnerBox);
                }
            }
        }

        this.innerBoxes.clear();
    }

    public void addBox(final AABB box) {
        if (this.depth < MAXIMUM_DEPTH && this.innerBoxes.size() > SUBDIVIDE_THRESHOLD) {
            this.subdivide();
        }

        if (!this.childrenOctants.isEmpty()) {
            for (int i = 0, size = this.childrenOctants.size(); i < size; i++) {
                final BoxOctree octree = this.childrenOctants.get(i);
                if (octree.boundaryIntersects(box)) {
                    octree.addBox(box);
                }
            }
        } else {
            // Prevent re-adding the same box if it already exists
            for (int i = 0, size = this.innerBoxes.size(); i < size; i++) {
                if (this.innerBoxes.get(i).equals(box)) {
                    return;
                }
            }

            this.innerBoxes.add(box);
        }
    }

    private boolean boundaryEntirelyContains(final AABB box) {
        return this.boundary.contains(box.minX, box.minY, box.minZ)
            && this.boundary.contains(box.maxX, box.maxY, box.maxZ);
    }

    private boolean boundaryIntersects(final AABB box) {
        return this.boundary.intersects(box);
    }

    /**
     * Equivalent of {@code !Shapes.joinIsNotEmpty(free, Shapes.create(box), BooleanOp.ONLY_SECOND)},
     * where {@code free} is the boundary minus all added boxes.
     */
    public boolean withinBoundsButNotIntersectingChildren(final AABB box) {
        return this.boundaryEntirelyContains(box) && !this.intersectsAnyBox(box);
    }

    private boolean intersectsAnyBox(final AABB box) {
        if (!this.childrenOctants.isEmpty()) {
            for (int i = 0, size = this.childrenOctants.size(); i < size; i++) {
                final BoxOctree octree = this.childrenOctants.get(i);
                if (octree.boundaryIntersects(box) && octree.intersectsAnyBox(box)) {
                    return true;
                }
            }
        } else {
            for (int i = 0, size = this.innerBoxes.size(); i < size; i++) {
                if (this.innerBoxes.get(i).intersects(box)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean boundaryContains(final BlockPos pos) {
        return this.boundary.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean withinAnyBox(final BlockPos pos) {
        if (!this.childrenOctants.isEmpty()) {
            for (int i = 0, size = this.childrenOctants.size(); i < size; i++) {
                final BoxOctree octree = this.childrenOctants.get(i);
                if (octree.boundaryContains(pos) && octree.withinAnyBox(pos)) {
                    return true;
                }
            }
        } else {
            for (int i = 0, size = this.innerBoxes.size(); i < size; i++) {
                if (this.innerBoxes.get(i).contains(pos.getX(), pos.getY(), pos.getZ())) {
                    return true;
                }
            }
        }

        return false;
    }
}
