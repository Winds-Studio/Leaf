package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncContainerUpdates extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-container-updates";
    }

    public static boolean enabled = false;
    public static boolean attemptVirtualThreads = true;
    private static boolean asyncContainerUpdatesInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Makes container change synchronization (slot updates, carried item, data slots) partially asynchronous.
                This can help reduce main thread load from inventory operations, especially with many updates.
                Listeners are still notified synchronously on the main thread.""",
            """
                使容器变更同步 (物品栏槽位更新, 鼠标拾取物品, 数据槽) 部分异步化.
                这有助于减少因物品栏操作导致的主线程负载, 尤其是在有大量更新时.
                监听器仍将在主线程上同步通知.""");

        if (asyncContainerUpdatesInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncContainerUpdatesInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        attemptVirtualThreads = config.getBoolean(getBasePath() + ".attempt-virtual-threads", attemptVirtualThreads);
    }
}
