package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK)
public class AlternativeJoin implements ConfigModule {

    @ConfigInfo(name = "async-switch-state", comments = {
        "Async switch connection state.",
        "异步切换连接状态."
    })
    public static boolean enabled = false;
}
