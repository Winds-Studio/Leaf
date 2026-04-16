package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ItemMergeMaxNeighboursPerScan extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static int maxNeighboursPerItemMergeScan = 16;

    @Override
    public void onLoaded() {
        maxNeighboursPerItemMergeScan = config.getInt(getBasePath() + ".max-neighbours-per-item-merge-scan", maxNeighboursPerItemMergeScan,
            config.pickStringRegionBased(
                """
                    Maximum number of nearby item entities a single item merge scan may inspect.
                    This can help prevent nearby item merge checks from consuming too much server thread time when many dropped items pile up in a small area.
                    Set to -1 to disable this limit.
                    """,
                """
                    单次物品合并时, 最多检查多少个附近的物品实体.
                    可用于防止局部大量掉落物堆积时, 邻近合并逻辑占用过多主线程时间.
                    设置为 -1 以禁用该限制.
                    """
            ));
    }
}
