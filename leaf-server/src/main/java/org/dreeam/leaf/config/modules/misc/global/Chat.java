package org.dreeam.leaf.config.modules.misc.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "chat-logging")
public final class Chat implements ConfigModule {

    @ConfigInfo(name = "empty-message-warning")
    public static boolean emptyMessageWarning = false;

    @ConfigInfo(name = "expired-message-warning")
    public static boolean expiredMessageWarning = false;

    @ConfigInfo(name = "not-secure-marker")
    public static boolean notSecureMarker = true;
}
