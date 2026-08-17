package org.dreeam.leaf.config.modules.network.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "premium-account-slow-login-timeout")
public final class PremiumAccountSlowLoginTimeout implements ConfigModule {

    @ConfigInfo(name = "ticks")
    public static int ticks = -1;
}
