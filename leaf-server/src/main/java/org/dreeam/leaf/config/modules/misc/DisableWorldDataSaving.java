package org.dreeam.leaf.config.modules.misc;

import net.minecraft.server.level.ServerLevel;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

import java.util.ArrayList;
import java.util.List;

public class DisableWorldDataSaving extends ConfigModule {

    public static List<String> worlds = new ArrayList<>();

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".disable-world-data-saving";
    }

    @Override
    public void onLoaded() {
        worlds = globalConfig.getList(basePath() + ".worlds", worlds,
            globalConfig.pickStringRegionBased("""
                    Worlds listed here will skip world data persistence.
                    Changes in chunks/entities remain in memory until unload/restart and are not written to disk.""",
                """
                    此处列出的世界将跳过世界数据持久化。
                    区块/实体更改仅保留在内存中直到卸载或重启，不会写入磁盘。"""));
    }

    public static boolean shouldSkipSave(ServerLevel serverLevel) {
        return !worlds.isEmpty() && worlds.contains(serverLevel.getWorld().getName());
    }
}
