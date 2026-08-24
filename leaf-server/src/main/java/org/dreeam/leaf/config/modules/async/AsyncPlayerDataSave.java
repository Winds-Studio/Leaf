package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "async-playerdata-save", comments = {
    """
        Make PlayerData saving asynchronously.""",
    """
        异步保存玩家数据."""
})
public class AsyncPlayerDataSave implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        if (enabled) {
            org.dreeam.leaf.async.AsyncPlayerDataSaving.init();
        }
    }
}
