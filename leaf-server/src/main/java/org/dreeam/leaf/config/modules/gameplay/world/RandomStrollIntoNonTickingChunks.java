package org.dreeam.leaf.config.modules.gameplay.world;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public final class RandomStrollIntoNonTickingChunks implements WorldConfigModule {

    @ConfigInfo(name = "entities-can-random-stroll-into-non-ticking-chunks")
    public boolean enabled = true;
}
