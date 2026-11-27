package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class DontSaveEntity extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".dont-save-entity";
    }

    public static boolean dontSavePrimedTNT = false;
    public static boolean dontSaveFallingBlock = false;

    @Override
    public void onLoaded() {
        dontSavePrimedTNT = globalConfig.getBoolean(basePath() + ".dont-save-primed-tnt", dontSavePrimedTNT,
            globalConfig.pickStringRegionBased("""
                    Disable save primed tnt on chunk unloads.
                    Useful for redstone/technical servers, can prevent machines from being exploded by TNT,
                    when player disconnected caused by Internet issue.""",
                """
                    区块卸载时不保存掉落的方块和激活的 TNT,
                    可以避免在玩家掉线时机器被炸毁."""));
        dontSaveFallingBlock = globalConfig.getBoolean(basePath() + ".dont-save-falling-block", dontSaveFallingBlock);
    }
}
