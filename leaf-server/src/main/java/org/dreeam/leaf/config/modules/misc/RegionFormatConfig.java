package org.dreeam.leaf.config.modules.misc;

import me.earthme.luminol.enums.EnumRegionFormat;
import me.earthme.luminol.utils.BufferedLinearRegionFileFlusher;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

public class RegionFormatConfig extends ConfigModule {

    public String basePath() {
        return ConfigCategory.MISC.basePath() + ".region-format";
    }

    public static @HotReloadUnsupported String regionFormatName = "MCA";
    public static @HotReloadUnsupported int compressionLevel = 6;
    public static @HotReloadUnsupported int ioThreadCount = 6;
    public static @HotReloadUnsupported int ioFlushDelay = -1;

    public static @DoNotLoad EnumRegionFormat regionFormat = EnumRegionFormat.MCA;
    public static @DoNotLoad BufferedLinearRegionFileFlusher blinearFlusher = null;

    private static boolean regionFormatLoaded = false;

    public static void closeFlusherIfEnabled() {
        if (blinearFlusher != null) {
            blinearFlusher.shutdown();
        }
    }

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Buffered Linear is a region format that uses zstd compression instead of zlib.
                Read Leaf docs before using!""",
            """
                Buffered Linear 是一种使用 zstd 压缩而非 ZLIB 的区域格式.
                使用前请阅读 Leaf 文档!""");

        if (regionFormatLoaded) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        regionFormatLoaded = true;

        regionFormatName = globalConfig.getString(basePath() + ".format-name", regionFormatName,
            globalConfig.pickStringRegionBased(
                "Available region format names: MCA, B_LINEAR",
                "可用格式: MCA, B_LINEAR"));
        compressionLevel = globalConfig.getInt(basePath() + ".compress-level", compressionLevel);
        ioThreadCount = globalConfig.getInt(basePath() + ".io-thread-count", ioThreadCount);
        ioFlushDelay = globalConfig.getInt(basePath() + ".io-flush-delay", ioFlushDelay);

        regionFormat = EnumRegionFormat.fromString(regionFormatName);
        if (regionFormat == EnumRegionFormat.UNKNOWN) {
            LOGGER.error("Unknown region format type {}! Falling back to MCA format.", regionFormatName);
            regionFormat = EnumRegionFormat.MCA;
            return;
        }

        if (regionFormat == EnumRegionFormat.B_LINEAR) {
            final int ioFlushDelay = RegionFormatConfig.ioFlushDelay <= 0 ? 3000 : RegionFormatConfig.ioFlushDelay;
            blinearFlusher = new BufferedLinearRegionFileFlusher(ioThreadCount, 20, ioFlushDelay);

            checkCompressionLevel();
        }
    }

    private static void checkCompressionLevel() {
        if (compressionLevel > 22 || compressionLevel < 1) {
            LOGGER.error("Buffered Linear region compression level should be between 1 and 22, but got {} in config", compressionLevel);
            LOGGER.error("Falling back to compression level 1.");
            compressionLevel = 1;
        }
    }
}
