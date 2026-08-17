package org.dreeam.leaf.config.modules.network.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "chat-order-verification")
public final class ChatOrderVerification implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = true;
}
