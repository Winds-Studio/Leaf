package org.dreeam.leaf.config.modules.misc;

import net.minecraft.server.level.ServerLevel;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

import java.util.ArrayList;
import java.util.List;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "disable-world-data-saving")
public class DisableWorldDataSaving implements ConfigModule {

    @ConfigInfo(name = "worlds", comments = {
        """
            Worlds listed here will skip world data persistence.
            Changes in chunks/entities remain in memory until unload/restart and are not written to disk.""",
        """
            此处列出的世界将跳过世界数据持久化。
            区块/实体更改仅保留在内存中直到卸载或重启，不会写入磁盘。"""
    })
    public static List<String> worlds = new ArrayList<>();

    public static boolean shouldSkipSave(ServerLevel serverLevel) {
        return !worlds.isEmpty() && worlds.contains(serverLevel.getWorld().getName());
    }
}
