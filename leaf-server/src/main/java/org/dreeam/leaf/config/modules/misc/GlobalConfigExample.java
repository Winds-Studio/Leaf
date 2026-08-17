package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "global-config-example", comments = {
    "An example global configuration section.",
    "一个全局配置节示例。"
})
public final class GlobalConfigExample implements ConfigModule {

    @ConfigInfo(name = "reloadable-value", comments = {
        "An example global value that supports hot reload.",
        "一个支持热重载的全局配置示例。"
    })
    public static String reloadableValue = "global-default";

    @HotReloadUnsupported
    @ConfigInfo(name = "restart-required-value", comments = {
        "An example global value that requires a restart.",
        "一个需要重启才能生效的全局配置示例。"
    })
    public static String restartRequiredValue = "global-restart-required";

    @DoNotLoad
    public static String runtimeValue;

    @Override
    public void onLoaded() {
        runtimeValue = reloadableValue + ':' + restartRequiredValue;
    }
}
