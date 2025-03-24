package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class AsyncBlockFinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-block-finding";
    }

    @Experimental
    public static boolean enabled = false;

    public static boolean asyncBlockFindingInitialized;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                **Experimental feature**
                This moves the expensive search calculations to a background thread while
                keeping the actual block validation on the main thread.""",
            """
                这会将昂贵的搜索计算移至后台线程, 同时在主线程上保持实际的方块验证.""");

        if (!asyncBlockFindingInitialized) {
            asyncBlockFindingInitialized = true;
            enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        }
    }
}
