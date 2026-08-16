package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class DenseSectionPushIndex extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".dense-section-push-index";
    }

    @Experimental
    public static boolean enabled = false;
    public static int threshold = 300;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Index entities by block position instead of scanning the whole section
                when a section has more than `threshold` entities in it.""",
            """
                当一个 section 内实体数超过 threshold 时,
                按方块坐标建索引代替全量扫描.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        threshold = globalConfig.getInt(basePath() + ".threshold", threshold);
    }
}
