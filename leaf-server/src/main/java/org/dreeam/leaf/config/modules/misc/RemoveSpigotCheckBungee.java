package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC)
public class RemoveSpigotCheckBungee implements ConfigModule {

    @ConfigInfo(name = "remove-spigot-check-bungee-config", comments = {
        """
            Enable player enter backend server through proxy
            without backend server enabling its bungee mode.""",
        """
            使服务器无需打开 bungee 模式即可让玩家加入后端服务器."""
    })
    public static boolean enabled = false;
}
