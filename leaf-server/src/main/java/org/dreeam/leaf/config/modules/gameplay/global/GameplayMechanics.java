package org.dreeam.leaf.config.modules.gameplay.global;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "book-writing")
public final class GameplayMechanics implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enableBookWriting = true;
}
