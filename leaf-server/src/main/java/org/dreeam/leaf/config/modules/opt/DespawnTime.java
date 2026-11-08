package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "despawn-time")
public class DespawnTime implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "proactive-weak-loading-despawn", comments = {
        """
            Proactive despawn check for weak-loaded entities.
            This is an experimental feature.""",
        """
            启用主动弱加载实体消失检查，
            这是一个实验性功能。"""
    })
    public static boolean proactiveWeakLoading = false;
}
