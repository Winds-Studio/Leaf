
package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class AsyncTargetFinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-target-finding";
    }

    public static boolean enabled = false;
    public static boolean alertOther = true;
    public static boolean searchBlock = true;
    public static boolean searchEntity = true;
    public static int queueSize = 4096;
    public static long threshold = 10L;
    private static boolean asyncTargetFindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                This moves the expensive entity and block search calculations to background thread while
                keeping the actual validation on the main thread.""",
            """
                这会将昂贵的实体目标搜索计算移至后台线程, 同时在主线程上保持实际的实体验证.""");

        if (asyncTargetFindingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncTargetFindingInitialized = true;

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        alertOther = config.getBoolean(getBasePath() + ".async-alert-other", true);
        searchBlock = config.getBoolean(getBasePath() + ".async-search-block", true);
        searchEntity = config.getBoolean(getBasePath() + ".async-search-entity", true);
        queueSize = config.getInt(getBasePath() + ".queue-size", 0);
        threshold = config.getLong(getBasePath() + ".threshold", 0);

        if (queueSize <= 0) {
            queueSize = 4096;
        }
        if (threshold <= 0L) {
            threshold = 10L;
        }
        if (!enabled) {
            alertOther = false;
            searchEntity = false;
            searchBlock = false;
        }
    }
}
