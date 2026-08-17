package org.dreeam.leaf.config.modules.opt.world;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldConfigModule;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "max-projectile-chunk-loads")
public final class MaxProjectileChunkLoads implements WorldConfigModule {

    @ConfigInfo(name = "per-tick")
    public int perTick = 10;

    @ConfigInfo(name = "per-projectile.max")
    public int perProjectileMax = 10;

    @ConfigInfo(name = "per-projectile.reset-movement-after-reach-limit")
    public boolean perProjectileResetMovementAfterReachLimit = false;

    @ConfigInfo(name = "per-projectile.remove-from-world-after-reach-limit")
    public boolean perProjectileRemoveFromWorldAfterReachLimit = false;
}
