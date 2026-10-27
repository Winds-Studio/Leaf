package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class OptimizeStructureLayout extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".optimize-structure-layout", enabled,
            globalConfig.pickStringRegionBased(
                """
                    Use an octree instead of voxel shapes to check free space of jigsaw structures,
                    and skip placement attempts that are known to fail. Keeps vanilla seed parity.
                    Based on StructureLayoutOptimizer.""",
                """
                    使用八叉树代替体素形状检查拼图结构的剩余空间,
                    并跳过必定失败的放置尝试. 保持原版种子一致性.
                    基于 StructureLayoutOptimizer."""));
    }
}
