package org.dreeam.leaf.config.modules.network.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "keepalive")
public final class Keepalive implements ConfigModule {

    @ConfigInfo(name = "send-multiple")
    public static boolean sendMultiple = false;
}
