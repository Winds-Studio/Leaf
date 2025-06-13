package org.dreeam.leaf.util.map;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BlockPosIterator extends AbstractIterator<BlockPos> {

    private final int startX;
    private final int startY;
    private final int startZ;
    private final int endX;
    private final int endY;
    private final int endZ;
    private @Nullable MutableBlockPos pos = null;

    public static Iterable<BlockPos> iterable(AABB bb) {
        return () -> new BlockPosIterator(bb);
    }

    public static Iterable<BlockPos> traverseArea(Vec3 vec, AABB boundingBox) {
        double toTravel = Math.min(16.0 / vec.length(), 1.0);
        Vec3 movement = vec.scale(toTravel);
        AABB fromBB = boundingBox.move(-vec.x, -vec.y, -vec.z);
        AABB searchArea = fromBB.expandTowards(movement);
        return iterable(searchArea);
    }

    public BlockPosIterator(AABB bb) {
        this.startX = Mth.floor(bb.minX);
        this.startY = Mth.floor(bb.minY);
        this.startZ = Mth.floor(bb.minZ);
        this.endX = Mth.floor(bb.maxX);
        this.endY = Mth.floor(bb.maxY);
        this.endZ = Mth.floor(bb.maxZ);
    }

    @Override
    protected BlockPos computeNext() {
        MutableBlockPos pos = this.pos;
        if (pos == null) {
            return this.pos = new MutableBlockPos(this.startX, this.startY, this.startZ);
        }
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
            y = this.startY; // Reset y also!
        } else {
            return this.endOfData();
        }

        pos.set(x, y, z);
        return pos;
    }
}
