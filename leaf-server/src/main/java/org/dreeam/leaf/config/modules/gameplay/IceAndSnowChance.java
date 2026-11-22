package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY)
public class IceAndSnowChance implements ConfigModule {

    @ConfigInfo(name = "ice-and-snow-chance")
    public static int rawIceAndSnowChance = 48;

    // The chance after normalization; read at runtime instead of the option itself.
    public static @DoNotLoad int iceAndSnowChance = 48;

    @Override
    public void onLoaded() {
        iceAndSnowChance = rawIceAndSnowChance <= 0 ? 48 : rawIceAndSnowChance;
    }
}
