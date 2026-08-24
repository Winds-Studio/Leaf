package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "chat-order-verification")
public final class ChatOrderVerification implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = true;
}
