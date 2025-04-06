package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class VT4DownloadPool extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".use-virtual-thread-for-download-pool", enabled,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for download worker pool.",
                "是否为下载工作线程池使用虚拟线程（如果可用）。"));
    }
}
