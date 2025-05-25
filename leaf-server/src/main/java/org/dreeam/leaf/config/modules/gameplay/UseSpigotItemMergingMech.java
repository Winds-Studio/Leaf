package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class UseSpigotItemMergingMech extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".use-spigot-item-merging-mechanism";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath(), enabled, config.pickStringRegionBased("""
                If enabled, always merge items into the current stack (Spigot logic)
                otherwise, prefer the smaller stack.""",
            """
                启用后总是将物品合并到当前堆叠（Spigot 逻辑）
                否则优先合并到数量较小的堆叠。"""));
    }
}
