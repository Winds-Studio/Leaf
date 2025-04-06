
package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class AsyncTargetFinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-target-finding";
    }

    @Experimental
    public static boolean enabled = false;
    public static boolean asyncTargetFindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                **Experimental feature**
                This moves the expensive entity target search calculations to a background thread while
                keeping the actual entity validation on the main thread.""",
            """
                这会将昂贵的实体目标搜索计算移至后台线程, 同时在主线程上保持实际的实体验证.""");

        if (!asyncTargetFindingInitialized) {
            asyncTargetFindingInitialized = true;
            enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        }
    }
}
