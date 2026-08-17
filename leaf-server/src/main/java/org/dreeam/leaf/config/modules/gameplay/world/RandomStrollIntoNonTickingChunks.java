package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "random-stroll-into-non-ticking-chunks")
public final class RandomStrollIntoNonTickingChunks implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = true;
}
