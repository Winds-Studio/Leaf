package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class FastRNG extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".faster-random-generator";
    }

    public static boolean enabled = false;
    public static boolean enableForWorldgen = false;
    public static boolean warnForSlimeChunk = true;
    public static boolean useLegacyForSlimeChunk = false;

    public static boolean worldgen = false;
    public static boolean worldgenEnabled() {
        return worldgen;
    } // Helper function

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Use faster random generator?
                Requires a JVM that supports Xoroshiro128PlusPlus.
                Some JREs don't support this.""",
            """
                是否使用更快的随机生成器?
                需要支持 Xoroshiro128PlusPlus 的 JVM.
                一些 JRE 不支持此功能.""");

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        enableForWorldgen = globalConfig.getBoolean(basePath() + ".enable-for-worldgen", enableForWorldgen,
            globalConfig.pickStringRegionBased(
                """
                    Enable faster random generator for world generation.
                    WARNING: This will affect world generation!!!""",
                """
                    是否为世界生成启用更快的随机生成器.
                    警告: 此项会影响世界生成!!!"""));
        warnForSlimeChunk = globalConfig.getBoolean(basePath() + ".warn-for-slime-chunk", warnForSlimeChunk,
            globalConfig.pickStringRegionBased(
                "Warn if you are not using legacy random source for slime chunk generation.",
                "是否在没有为史莱姆区块使用原版随机生成器的情况下进行警告."));
        useLegacyForSlimeChunk = globalConfig.getBoolean(basePath() + ".use-legacy-random-for-slime-chunk", useLegacyForSlimeChunk, globalConfig.pickStringRegionBased(
            """
                Use legacy random source for slime chunk generation,
                to follow vanilla behavior.""",
            """
                是否使用原版随机生成器来生成史莱姆区块."""));
        if (enabled) {
            try {
                Class.forName("org.dreeam.leaf.util.math.random.FasterRandomSource");
            } catch (Throwable ignored) {
                LeafConfig.LOGGER.error("Faster random generator is enabled but Xoroshiro128PlusPlus is not supported by your JVM, " +
                    "falling back to legacy random source.");
                enabled = false;
            }
        }

        if (enabled && warnForSlimeChunk) {
            LeafConfig.LOGGER.warn("You enabled faster random generator, it will offset location of slime chunk");
            LeafConfig.LOGGER.warn("If your server has slime farms or facilities need vanilla slime chunk,");
            LeafConfig.LOGGER.warn("set performance.faster-random-generator.use-legacy-random-for-slime-chunk " +
                "to true to use LegacyRandomSource for slime chunk generation.");
            LeafConfig.LOGGER.warn("Set performance.faster-random-generator.warn-for-slime-chunk to false to " +
                "disable this warning.");
        }

        worldgen = enableForWorldgen && enabled;
    }
}
