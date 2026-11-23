package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.ASYNC, name = "async-mob-spawning", comments = {
    """
        Whether or not asynchronous mob spawning should be enabled.
        On servers with many entities, this can improve performance by up to 15%. You must have
        paper's per-player-mob-spawns setting set to true for this to work.
        One quick note - this does not actually spawn mobs async (that would be very unsafe).
        This just offloads some expensive calculations that are required for mob spawning.""",
    """
        是否异步化生物生成.
        在实体较多的服务器上, 异步生成可最高带来 15% 的性能提升.
        须在Paper配置文件中打开 per-player-mob-spawns 才能生效."""
})
public class AsyncMobSpawning implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = true;
}
