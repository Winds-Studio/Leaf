package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class IceAndSnowChance implements ConfigModule {

    @ConfigInfo(name = "ice-and-snow-chance")
    public static int iceAndSnowChance = 48;

    @Override
    public void onLoaded() {
        if (iceAndSnowChance <= 0) {
            iceAndSnowChance = 48;
        }
    }
}
