package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class AsyncPathfinding extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.ASYNC.getBaseKeyName() + ".async-pathfinding";
    }

    public static boolean enabled = false;
    private static boolean asyncPathfindingInitialized;

    @Override
    public void onLoaded() {
        if (asyncPathfindingInitialized) {
            config.getConfigSection(getBasePath());
            return;
        }
        asyncPathfindingInitialized = true;
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
    }
}
