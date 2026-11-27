package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class VirtualThreadSupport extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".use-virtual-thread";
    }

    public static boolean bukkitAsyncScheduler = false;
    public static boolean foliaAsyncScheduler = false;
    public static boolean asyncChatExecutor = true;
    public static boolean downloadPool = false;

    @Override
    public void onLoaded() {
        bukkitAsyncScheduler = globalConfig.getBoolean(basePath() + ".bukkit-async-scheduler", bukkitAsyncScheduler,
            globalConfig.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for CraftAsyncScheduler.",
                "是否为 Bukkit 异步任务调度器使用虚拟线程."));
        foliaAsyncScheduler = globalConfig.getBoolean(basePath() + ".folia-async-scheduler", foliaAsyncScheduler,
            globalConfig.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for FoliaAsyncScheduler.",
                "是否为 Folia 异步任务调度器使用虚拟线程."));
        asyncChatExecutor = globalConfig.getBoolean(basePath() + ".async-chat-executor", asyncChatExecutor,
            globalConfig.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for Async Chat Executor.",
                "是否为异步聊天线程使用虚拟线程."));
        downloadPool = globalConfig.getBoolean(basePath() + ".download-pool", downloadPool,
            globalConfig.pickStringRegionBased(
                "Use the new Virtual Thread introduced in JDK 21 for profile fetching executor.",
                "是否为档案查询执行器使用虚拟线程。"));
    }
}
