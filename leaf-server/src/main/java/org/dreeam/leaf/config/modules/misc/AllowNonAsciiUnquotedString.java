package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class AllowNonAsciiUnquotedString extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath();
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".allow-non-ascii-unquoted-string", enabled,
            globalConfig.pickStringRegionBased(
                "Allow non-ASCII characters in unquoted command arguments.",
                "允许命令参数的无引号字符串中使用非 ASCII 字符."
            ));
    }
}
