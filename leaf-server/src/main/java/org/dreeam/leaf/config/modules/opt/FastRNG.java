package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

import java.util.random.RandomGeneratorFactory;

public class FastRNG extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".faster-random-generator";
    }

    public static boolean enabled = false;
    public static boolean enableForWorldgen = false;
    public static String randomGenerator = "Xoroshiro128PlusPlus";
    public static boolean warnForSlimeChunk = true;
    public static boolean useLegacyForSlimeChunk = false;
    public static boolean useDirectImpl = false;

    public static boolean worldgenEnabled() {
        return enabled && enableForWorldgen;
    } // Helper function

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Use faster random generator?
                Requires a JVM that supports RandomGenerator.
                Some JREs don't support this.""",
            """
                是否使用更快的随机生成器?
                需要支持 RandomGenerator 的 JVM.
                一些 JRE 不支持此功能.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        randomGenerator = config.getString(getBasePath() + ".random-generator", randomGenerator,
            config.pickStringRegionBased(
                """
                    Which random generator will be used?
                    See https://openjdk.org/jeps/356""",
                """
                    使用什么种类的随机生成器.
                    请参阅 https://openjdk.org/jeps/356"""));
        enableForWorldgen = config.getBoolean(getBasePath() + ".enable-for-worldgen", enableForWorldgen,
            config.pickStringRegionBased(
                """
                    Enable faster random generator for world generation.
                    WARNING: This will affect world generation!!!""",
                """
                    是否为世界生成启用更快的随机生成器.
                    警告: 此项会影响世界生成!!!"""));
        warnForSlimeChunk = config.getBoolean(getBasePath() + ".warn-for-slime-chunk", warnForSlimeChunk,
            config.pickStringRegionBased(
                "Warn if you are not using legacy random source for slime chunk generation.",
                "是否在没有为史莱姆区块使用原版随机生成器的情况下进行警告."));
        useLegacyForSlimeChunk = config.getBoolean(getBasePath() + ".use-legacy-random-for-slime-chunk", useLegacyForSlimeChunk, config.pickStringRegionBased(
            """
                Use legacy random source for slime chunk generation,
                to follow vanilla behavior.""",
            """
                是否使用原版随机生成器来生成史莱姆区块."""));
        useDirectImpl = config.getBoolean(getBasePath() + ".use-direct-implementation", useDirectImpl,
            config.pickStringRegionBased(
                """
                    Use direct random implementation instead of delegating to Java's RandomGenerator.
                    This may improve performance but potentially changes RNG behavior.""",
                """
                    使用直接随机实现而不是委派给 RandomGenerator.
                    这可能会提高性能, 但可能会改变 RNG 行为."""));

        if (enabled) {
            try {
                RandomGeneratorFactory.of(randomGenerator);
            } catch (Exception e) {
                LeafConfig.LOGGER.error("Faster random generator is enabled but {} is not supported by your JVM, " +
                    "falling back to legacy random source.", randomGenerator);
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
    }
}
