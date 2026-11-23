package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC)
public class RemoveChangeNonEditableSignWarning implements ConfigModule {

    @ConfigInfo(name = "remove-change-non-editable-sign-warning", comments = {
        "Enable to prevent console spam.",
        "移除修改无法编辑的告示牌时输出的警告."
    })
    public static boolean enabled = false;
}
