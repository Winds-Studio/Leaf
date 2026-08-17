package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "ender-dragon-respawn")
public final class EnderDragonRespawn implements WorldConfigModule {

    @ConfigInfo(name = "try-after-end-crystal-place")
    public boolean tryAfterEndCrystalPlace = true;
}
