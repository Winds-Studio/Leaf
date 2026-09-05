package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "use-virtual-thread")
public class VirtualThreadSupport implements ConfigModule {

    @ConfigInfo(name = "bukkit-async-scheduler", comments = {"Use the new Virtual Thread introduced in JDK 21 for CraftAsyncScheduler.", "是否为 Bukkit 异步任务调度器使用虚拟线程."})
    public static boolean bukkitAsyncScheduler = false;
    @ConfigInfo(name = "folia-async-scheduler", comments = {"Use the new Virtual Thread introduced in JDK 21 for FoliaAsyncScheduler.", "是否为 Folia 异步任务调度器使用虚拟线程."})
    public static boolean foliaAsyncScheduler = false;
    @ConfigInfo(name = "async-chat-executor", comments = {"Use the new Virtual Thread introduced in JDK 21 for Async Chat Executor.", "是否为异步聊天线程使用虚拟线程."})
    public static boolean asyncChatExecutor = true;
    @ConfigInfo(name = "download-pool", comments = {"Use the new Virtual Thread introduced in JDK 21 for profile fetching executor.", "是否为档案查询执行器使用虚拟线程。"})
    public static boolean downloadPool = false;
}
