package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class Including5sIngetTPS extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".including-5s-in-get-tps", enabled);
    }
}
