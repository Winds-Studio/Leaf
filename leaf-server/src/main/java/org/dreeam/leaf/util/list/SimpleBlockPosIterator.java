package org.dreeam.leaf.util.list;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SimpleBlockPosIterator extends AbstractIterator<BlockPos> {
    private final int startX;
    private final int startY;
    private final int startZ;
    private final int endX;
    private final int endY;
    private final int endZ;
    private @Nullable MutableBlockPos pos = null;

    public static Iterable<BlockPos> iterable(final AABB bounds) {
        return () -> new SimpleBlockPosIterator(bounds);
    }

    public static Iterable<BlockPos> traverseBoundsInDirection(final Vec3 direction, final AABB bounds, final double maxDistance) {
        final double scaledDistance = Math.min(maxDistance / direction.length(), 1.0);
        final AABB previousBounds = bounds.move(direction.scale(-1.0));
        final AABB boundsToSearch = previousBounds.expandTowards(direction.scale(scaledDistance));
        return SimpleBlockPosIterator.iterable(boundsToSearch);
    }

    public SimpleBlockPosIterator(final AABB bounds) {
        this.startX = Mth.floor(bounds.minX);
        this.startY = Mth.floor(bounds.minY);
        this.startZ = Mth.floor(bounds.minZ);
        this.endX = Mth.floor(bounds.maxX);
        this.endY = Mth.floor(bounds.maxY);
        this.endZ = Mth.floor(bounds.maxZ);
    }

    @Override
    protected BlockPos computeNext() {
        final MutableBlockPos pos = this.pos;
        if (pos == null) {
            return this.pos = new MutableBlockPos(this.startX, this.startY, this.startZ);
        } else {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            if (y < this.endY) {
                y += 1;
            } else if (x < this.endX) {
                x += 1;
                y = this.startY;
            } else if (z < this.endZ) {
                z += 1;
                x = this.startX;
                y = this.startY;
            } else {
                return this.endOfData();
            }

            pos.set(x, y, z);
            return pos;
        }
    }
}
