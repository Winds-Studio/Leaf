package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "dont-save-entity")
public class DontSaveEntity implements ConfigModule {

    @ConfigInfo(name = "dont-save-primed-tnt", comments = {
        """
            Disable save primed tnt on chunk unloads.
            Useful for redstone/technical servers, can prevent machines from being exploded by TNT,
            when player disconnected caused by Internet issue.""",
        """
            区块卸载时不保存掉落的方块和激活的 TNT,
            可以避免在玩家掉线时机器被炸毁."""
    })
    public static boolean dontSavePrimedTNT = false;

    @ConfigInfo(name = "dont-save-falling-block")
    public static boolean dontSaveFallingBlock = false;
}
