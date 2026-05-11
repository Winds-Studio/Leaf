package org.dreeam.leaf.config.modules.misc;

import net.minecraft.server.level.ServerLevel;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

import java.util.ArrayList;
import java.util.List;

public class DisableWorldDataSaving extends ConfigModules {

    public static List<String> worlds = new ArrayList<>();

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".disable-world-data-saving";
    }

    @Override
    public void onLoaded() {
        worlds = config.getList(getBasePath() + ".worlds", worlds,
            config.pickStringRegionBased("""
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
