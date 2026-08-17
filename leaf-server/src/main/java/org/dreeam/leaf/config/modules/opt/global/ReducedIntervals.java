package org.dreeam.leaf.config.modules.opt.global;

import net.minecraft.server.level.ServerPlayer;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "reduced-intervals")
public final class ReducedIntervals implements ConfigModule {

    @ConfigInfo(name = "increase-time-statistics")
    public static int increaseTimeStatistics = 1;

    @ConfigInfo(name = "update-entity-line-of-sight")
    public static int updateEntityLineOfSight = 4;

    @Override
    public void onLoaded() {
        ServerPlayer.increaseTimeStatisticsInterval = Math.max(1, increaseTimeStatistics);
    }
}
