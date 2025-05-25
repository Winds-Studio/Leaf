package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ReduceUselessPackets extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".reduce-packets";
    }

    public static boolean reduceUselessEntityMovePackets = false;

    @Override
    public void onLoaded() {
        reduceUselessEntityMovePackets = config.getBoolean(getBasePath() + ".reduce-entity-move-packets", reduceUselessEntityMovePackets, config.pickStringRegionBased( """
            Reduces bandwidth by skipping unnecessary entity movement packets.
            Helpful for improving network and client performance.""",
            """
                减少不必要的实体移动数据包发送，
                降低带宽占用，提升性能"""));
    }
}
