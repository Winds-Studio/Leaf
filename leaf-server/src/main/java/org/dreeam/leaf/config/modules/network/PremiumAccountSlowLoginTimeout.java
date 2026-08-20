package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "premium-account-slow-login-timeout")
public final class PremiumAccountSlowLoginTimeout implements ConfigModule {

    @ConfigInfo(name = "ticks")
    public static int ticks = -1;
}
