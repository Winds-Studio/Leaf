package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class DespawnTime extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".despawn-time";
    }

    @Experimental
    public static boolean proactiveWeakLoading = false;
    public static int maxEntityToProcess = 20;

    @Override
    public void onLoaded() {
        proactiveWeakLoading = config.getBoolean(getBasePath() + ".proactive-weak-loading-despawn", proactiveWeakLoading,
            config.pickStringRegionBased("""
                    Proactive despawn check for weak-loaded entities.
                    This is an experimental feature.""",
                """
                    启用主动弱加载实体消失检查，
                    这是一个实验性功能。"""));
        maxEntityToProcess = config.getInt(getBasePath() + ".max-entity-to-process", maxEntityToProcess,
            config.pickStringRegionBased("""
                    Maximum amount of entities to process per tick.""",
                """
                    每刻处理最大实体数。
                    最低为当前实体量的 5%。"""));
    }
}
