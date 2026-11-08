package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class UseSpigotItemMergingMech implements ConfigModule {

    @ConfigInfo(name = "use-spigot-item-merging-mechanism")
    public static boolean enabled = false;
}
