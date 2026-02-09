package dev.tr7zw.entityculling;

import com.logisticscraft.occlusionculling.DataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class DefaultChunkDataProvider implements DataProvider {
    private final Level level;

    public DefaultChunkDataProvider(Level level) {
        this.level = level;
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        return this.level.getChunkIfLoaded(chunkX, chunkZ) != null;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);

        final ChunkAccess access = this.level.getChunkIfLoaded(pos);
        if (access == null) {
            return false;
        }

        if (this.level.isOutsideBuildHeight(pos)) {
            BlockState bs = Blocks.VOID_AIR.defaultBlockState();
            return !bs.canOcclude() && bs.isSolidRender();
        } else {
            BlockState bs = access.getBlockState(pos);
            return !bs.canOcclude() && bs.isSolidRender();
        }
    }

    @Override
    public void cleanup() {
        DataProvider.super.cleanup();
    }

}
