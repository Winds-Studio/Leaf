package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class ConfigurableInventoryOverflowEvent extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath() + ".inventory-overflow-event";
    }

    public static boolean enabled = false;
    public static String listenerClass = "com.example.package.PlayerInventoryOverflowEvent";

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled, globalConfig.pickStringRegionBased("""
                The event called when used plugin to Inventory#addItem
                into player's inventory, and the inventory is full.
                This is not recommended to use, please re-design to use the
                returned map of Inventory#addItem method as soon as possible!""",
            """
                此事件将在插件使用 Inventory#addItem 方法
                添加物品到玩家背包, 但是背包已满时调用.
                不建议使用此事件，请尽快迁移至使用 Inventory#addItem 方法
                返回的 map"""));
        listenerClass = globalConfig.getString(basePath() + ".listener-class", listenerClass, globalConfig.pickStringRegionBased("""
                The full class name of the listener which listens to this inventory overflow event.""",
            """
                监听此物品栏物品溢出事件的完整类名."""));
    }
}
