package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class VT4ProfileExecutor extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".use-virtual-thread-for-profile-executor", enabled,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for profile lookup executor.",
                "是否为档案查询执行器使用虚拟线程（如果可用）。"));
    }
}
