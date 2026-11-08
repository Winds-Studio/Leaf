package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "faster-random-generator", comments = {"Use faster random generator?\nRequires a JVM that supports Xoroshiro128PlusPlus.\nSome JREs don't support this.", "是否使用更快的随机生成器?\n需要支持 Xoroshiro128PlusPlus 的 JVM.\n一些 JRE 不支持此功能."})
public class FastRNG implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "enable-for-worldgen", comments = {"Enable faster random generator for world generation.\nWARNING: This will affect world generation!!!", "是否为世界生成启用更快的随机生成器.\n警告: 此项会影响世界生成!!!"})
    public static boolean enableForWorldgen = false;

    @ConfigInfo(name = "warn-for-slime-chunk", comments = {"Warn if you are not using legacy random source for slime chunk generation.", "是否在没有为史莱姆区块使用原版随机生成器的情况下进行警告."})
    public static boolean warnForSlimeChunk = true;

    @ConfigInfo(name = "use-legacy-random-for-slime-chunk", comments = {"Use legacy random source for slime chunk generation,\nto follow vanilla behavior.", "是否使用原版随机生成器来生成史莱姆区块."})
    public static boolean useLegacyForSlimeChunk = false;

    @DoNotLoad
    public static boolean worldgen = false;

    public static boolean worldgenEnabled() {
        return worldgen;
    } // Helper function

    @Override
    public void onLoaded() {
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
