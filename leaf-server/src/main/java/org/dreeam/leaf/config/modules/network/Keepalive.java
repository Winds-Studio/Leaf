package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "keepalive")
public final class Keepalive implements ConfigModule {

    @ConfigInfo(name = "send-multiple")
    public static boolean sendMultiple = false;
}
