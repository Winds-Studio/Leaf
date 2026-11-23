package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class IceAndSnowChance implements ConfigModule {

    @ConfigInfo(name = "ice-and-snow-chance")
    public static int rawIceAndSnowChance = 48;

    public static @DoNotLoad int iceAndSnowChance;

    @Override
    public void onLoaded() {
        iceAndSnowChance = rawIceAndSnowChance <= 0 ? 48 : rawIceAndSnowChance;
    }
}
