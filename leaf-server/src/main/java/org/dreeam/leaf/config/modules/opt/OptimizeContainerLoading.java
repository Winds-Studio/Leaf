package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OptimizeContainerLoading extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".optimize-container-loading";
    }

    public static boolean lazyLoadHopperItems = false;

    @Override
    public void onLoaded() {
        lazyLoadHopperItems = config.getBoolean(getBasePath() + ".lazy-load-hopper-items", lazyLoadHopperItems,
            config.pickStringRegionBased("""
                    Defers parsing of hopper item NBT until the items are actually accessed.
                    When a chunk loads, hopper block entities normally deserialize all 5 item
                    slots from NBT immediately. With this enabled, the items list stays empty
                    until the first read (container open, hopper transfer, plugin access, etc).
                    Significantly speeds up chunk loading on worlds with many hoppers (tech farms,
                    storage systems). Hoppers that are never accessed during their chunk lifetime
                    skip the deserialize cost entirely.
                    Note: plugins that read the 'items' NonNullList directly via reflection will
                    observe an empty list until first access goes through the Container interface
                    (Bukkit Inventory API access works correctly).""",
                """
                    将漏斗物品 NBT 的解析推迟到实际访问物品时。
                    区块加载时，漏斗方块实体通常会立即从 NBT 反序列化全部 5 个物品槽。
                    启用此选项后，物品列表会保持为空，直到首次读取
                    （容器打开、漏斗传输、插件访问等）。
                    对于拥有大量漏斗的世界（科技服务器、存储系统）能显著加速区块加载。
                    在区块生命周期内从未被访问的漏斗将完全跳过反序列化开销。
                    注意：通过反射直接读取 items NonNullList 字段的插件，在首次通过
                    Container 接口访问之前会看到空列表（Bukkit Inventory API 可正常工作）。"""));
    }
}
