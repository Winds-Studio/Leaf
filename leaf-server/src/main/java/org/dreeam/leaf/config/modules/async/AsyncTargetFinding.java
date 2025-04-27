
package org.dreeam.leaf.config.modules.async;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.dreeam.leaf.async.ai.AsyncGoalExecutor;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncTargetFinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-target-finding";
    }

    @Experimental
    public static boolean enabled = false;
    public static boolean alertOther = true;
    public static boolean searchBlock = false;
    public static boolean searchEntity = true;
    public static boolean searchPlayer = false;
    public static boolean searchPlayerTempt = false;
    private static boolean asyncTargetFindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                **Experimental feature**
                This moves the expensive entity target search calculations to a background thread while
                keeping the actual entity validation on the main thread.""",
            """
                **实验性功能**
                这会将昂贵的实体目标搜索计算移至后台线程, 同时在主线程上保持实际的实体验证.""");

        if (asyncTargetFindingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncTargetFindingInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        alertOther = config.getBoolean(getBasePath() + ".async-alert-other", true);
        searchBlock = config.getBoolean(getBasePath() + ".async-search-block", false);
        searchEntity = config.getBoolean(getBasePath() + ".async-search-entity", true);
        searchPlayer = config.getBoolean(getBasePath() + ".async-search-player", false);
        searchPlayerTempt = config.getBoolean(getBasePath() + ".async-search-player-tempt", false);
        if (!enabled) {
            alertOther = false;
            searchEntity = false;
            searchBlock = false;
            searchPlayer = false;
            searchPlayerTempt = false;
            return;
        }
        AsyncGoalExecutor.EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),
            new ThreadFactoryBuilder()
                .setNameFormat("Leaf Async Target Finding Thread")
                .setDaemon(true)
                .setPriority(Thread.NORM_PRIORITY - 2)
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
