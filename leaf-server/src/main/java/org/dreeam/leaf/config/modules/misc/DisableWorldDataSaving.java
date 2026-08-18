package org.dreeam.leaf.config.modules.misc;

import net.minecraft.server.level.ServerLevel;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.WorldList;

import java.util.ArrayList;

public class DisableWorldDataSaving extends ConfigModule {

    public static WorldList worlds = WorldList.EMPTY;

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".disable-world-data-saving";
    }

    @Override
    public void onLoaded() {
        worlds = new WorldList(globalConfig.getList(basePath() + ".worlds", new ArrayList<>(),
            globalConfig.pickStringRegionBased("""
                    Worlds listed here will skip world data persistence.
                    Changes in chunks/entities remain in memory until unload/restart and are not written to disk.
                    '*' works as a wildcard, e.g. 'hub*' matches hub01 and hub02, '*hub*' matches every world
                    whose name contains 'hub'. Entries without '*' are matched exactly.""",
                """
                    此处列出的世界将跳过世界数据持久化。
                    区块/实体更改仅保留在内存中直到卸载或重启，不会写入磁盘。
                    '*' 可作为通配符使用, 例如 'hub*' 匹配 hub01 和 hub02, '*hub*' 匹配名称中包含 'hub' 的所有世界。
                    不含 '*' 的条目按完整名称精确匹配。""")));
    }

    public static boolean shouldSkipSave(ServerLevel serverLevel) {
        return !worlds.isEmpty() && worlds.contains(serverLevel.getWorld().getName());
    }
}
