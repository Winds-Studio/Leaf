package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.FIXES, name = "prevent-moving-into-weak-loaded-chunks", comments = {
    "Prevents entities from moving into weak loaded chunks.",
    "阻止实体进入弱加载区块。"
})
public class PreventMoveIntoWeakLoadedChunks implements ConfigModule {

    @ConfigInfo(name = "enabled", comments = {
        "Set to true to enable features below.",
        "设置为 true 以启用以下功能。"
    })
    public static boolean enabled = false;
    @ConfigInfo(name = "projectiles", comments = {
        "Prevents projectiles from moving into weak loaded chunks.",
        "阻止弹射物进入弱加载区块。"
    })
    public static boolean projectiles = false;

    public static boolean isProjectileEnabled() {
        return enabled && projectiles;
    }
}
