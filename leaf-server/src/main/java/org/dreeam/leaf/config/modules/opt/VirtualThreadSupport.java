package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class VirtualThreadSupport extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".use-virtual-thread";
    }

    public static boolean bukkitAsyncScheduler = false;
    public static boolean foliaAsyncScheduler = false;
    public static boolean asyncChatExecutor = true;
    public static boolean downloadPool = false;

    @Override
    public void onLoaded() {
        bukkitAsyncScheduler = config.getBoolean(getBasePath() + ".bukkit-async-scheduler", bukkitAsyncScheduler,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for CraftAsyncScheduler.",
                "是否为 Bukkit 异步任务调度器使用虚拟线程."));
        foliaAsyncScheduler = config.getBoolean(getBasePath() + ".folia-async-scheduler", foliaAsyncScheduler,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for FoliaAsyncScheduler.",
                "是否为 Folia 异步任务调度器使用虚拟线程."));
        asyncChatExecutor = config.getBoolean(getBasePath() + ".async-chat-executor", asyncChatExecutor,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for Async Chat Executor.",
                "是否为异步聊天线程使用虚拟线程."));
        downloadPool = config.getBoolean(getBasePath() + ".download-pool", downloadPool,
            config.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for profile fetching executor.",
                "是否为档案查询执行器使用虚拟线程。"));
    }
}
