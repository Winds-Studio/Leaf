package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class DespawnTime extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".despawn-time";
    }

    @Experimental
    public static boolean activeWeakLoading = false;
    public static int maxEntityToProcess = 20;

    @Override
    public void onLoaded() {
        activeWeakLoading = config.getBoolean(getBasePath() + ".active-weak-loading-despawn", activeWeakLoading,
            config.pickStringRegionBased("""
                    Active despawn check for weak-loaded entities.
                    This is an experimental feature.""",
                """
                    启用主动弱加载实体消失检查,
                    这是一个实验性功能."""));
        maxEntityToProcess = config.getInt(getBasePath() + ".max-entity-to-process", maxEntityToProcess,
            config.pickStringRegionBased("""
                    Maximum amount of entities to process per tick.""",
                """
                    每刻处理最大实体数.
                    最低为当前实体量的 5%."""));
    }
}
