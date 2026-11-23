package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public final class EnderDragonRespawn implements WorldConfigModule {

    @ConfigInfo(name = "try-respawn-ender-dragon-after-end-crystal-place")
    public boolean tryAfterEndCrystalPlace = true;
}
