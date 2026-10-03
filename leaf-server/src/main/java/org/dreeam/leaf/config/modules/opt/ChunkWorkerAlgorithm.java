package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.chunk.ChunkSystemAlgorithm;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

import java.util.Locale;

public class ChunkWorkerAlgorithm extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".chunk-worker-algorithm";
    }

    public static ChunkSystemAlgorithm algorithm = ChunkSystemAlgorithm.MOONRISE;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Algorithm used to determine the number of worker threads for chunk loading and generation.
                Available algorithms:
                 - MOONRISE: Paper's default algorithm (CPU cores / 2).
                 - C2ME: Aggressive thread allocation considering both CPU cores and memory.
                 - C2ME_NEW: Balanced approach between MOONRISE and C2ME.""",
            """
                用于计算区块加载与生成工作线程数的算法.
                可选算法:
                 - MOONRISE: Paper 默认算法 (核心数 / 2).
                 - C2ME: 综合考虑 CPU 与内存的更激进分配算法.
                 - C2ME_NEW: 介于 MOONRISE 与 C2ME 之间的平衡算法.""");

        String name = globalConfig.getString(basePath(), algorithm.name());
        try {
            algorithm = ChunkSystemAlgorithm.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            algorithm = ChunkSystemAlgorithm.MOONRISE;
        }
    }
}
