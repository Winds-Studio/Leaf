package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "random-stroll-into-non-ticking-chunks")
public final class RandomStrollIntoNonTickingChunks implements WorldConfigModule {

    @ConfigInfo(name = "enabled")
    public boolean enabled = true;
}
