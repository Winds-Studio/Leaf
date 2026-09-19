package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.Experimental;

public class ContainerItemLazyLoading extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    @Experimental
    public static boolean lazyContainerItemLoading = false;

    @Override
    public void onLoaded() {
        lazyContainerItemLoading = globalConfig.getBoolean(basePath() + ".container-item-lazy-loading", lazyContainerItemLoading, globalConfig.pickStringRegionBased("""
                Experimental: Defer item decoding in chests, trapped chests, barrels and shulker boxes until inventory access.
                Retains the block entity's raw item NBT data until decoding, may increase memory usage.""",
            """
                实验性功能: 箱子、陷阱箱、木桶和潜影盒延迟到访问时解码物品,
                解码前保留方块实体的原始物品 NBT 数据, 可能增加内存占用."""));
    }
}
