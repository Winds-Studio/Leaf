package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class Including5sIngetTPS extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName();
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".including-5s-in-get-tps", enabled, config.pickStringRegionBased("If enabled, includes 5s average TPS in the result: [5s, 1m, 5m, 15m]; otherwise returns [1m, 5m, 15m].",
            "启用后返回 [5s, 1m, 5m, 15m] 的 TPS 值；否则仅返回 [1m, 5m, 15m]。"));
    }
}
