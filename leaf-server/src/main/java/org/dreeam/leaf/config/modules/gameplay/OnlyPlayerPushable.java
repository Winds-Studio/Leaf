package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class OnlyPlayerPushable implements ConfigModule {

    @ConfigInfo(name = "only-player-pushable", comments = {
        "Enable to make only player pushable",
        "是否只允许玩家被实体推动"
    })
    public static boolean enabled = false;
}
