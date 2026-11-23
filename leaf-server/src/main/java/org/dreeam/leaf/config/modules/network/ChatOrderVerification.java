package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK)
public final class ChatOrderVerification implements ConfigModule {

    @ConfigInfo(name = "verify-chat-order")
    public static boolean enabled = true;
}
