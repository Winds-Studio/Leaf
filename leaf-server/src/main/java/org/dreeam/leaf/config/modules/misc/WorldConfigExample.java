package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.MISC, name = "world-config-example", comments = {
    "An example world configuration section.",
    "一个世界配置节示例。"
})
public final class WorldConfigExample implements WorldConfigModule {

    @ConfigInfo(name = "restart-required-value", comments = {
        "An example world value that requires a restart.",
        "一个需要重启才能生效的世界配置示例。"
    })
    public String restartRequiredValue = "world-restart-required";
}
