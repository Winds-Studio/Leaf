package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK)
public final class PremiumAccountSlowLoginTimeout implements ConfigModule {

    @ConfigInfo(name = "premium-account-slow-login-timeout")
    public static int ticks = -1;
}
