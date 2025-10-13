package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class VT4BukkitScheduler extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".use-virtual-thread-for-async-scheduler", enabled,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for CraftAsyncScheduler.",
                "是否为异步任务调度器使用虚拟线程."));
    }
}
