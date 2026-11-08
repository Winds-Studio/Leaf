package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "book-writing")
public final class BookWriting implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = true;
}
