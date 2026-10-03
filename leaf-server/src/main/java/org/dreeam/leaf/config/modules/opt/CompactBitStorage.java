package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

public class CompactBitStorage extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".modernfix.compact-bit-storage";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Fixes memory waste caused by empty/uniform chunk sections stored with excess bits.""",
            """
                紧凑位存储优化, 减少单一/空方块区块段反序列化时的内存浪费.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
