package org.dreeam.leaf.config.modules.misc;

import abomination.LinearRegionFile;
import me.earthme.luminol.enums.EnumRegionFormat;
import me.earthme.luminol.utils.BufferedLinearRegionFileFlusher;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.dreeam.leaf.util.LeafConstants;

@HotReloadUnsupported
@ConfigClassInfo(category = ConfigCategory.MISC, name = "region-format", comments = {
    """
        Linear is a region format that uses zstd compression instead of zlib.
        This format saves about 50% of disk space.
        Read Leaf docs before using!""",
    """
        Linear 是一种使用 zstd 压缩而非 ZLIB 的区域格式.
        该格式可节省约 50% 的磁盘空间.
        使用前请阅读 Leaf 文档!"""
})
public class RegionFormatConfig implements ConfigModule {

    @ConfigInfo(name = "format-name", comments = {
        "Available region format names: MCA, B_LINEAR, LINEAR_V2",
        "可用格式: MCA, B_LINEAR, LINEAR_V2"
    })
    public static String regionFormatName = "MCA";

    @ConfigInfo(name = "compress-level")
    public static int compressionLevel = 6;

    @ConfigInfo(name = "io-thread-count")
    public static int ioThreadCount = 6;

    @ConfigInfo(name = "io-flush-delay")
    public static int ioFlushDelay = -1;

    @ConfigInfo(name = "linear-use-virtual-thread")
    public static boolean linearUseVirtualThread = true;

    public static @DoNotLoad EnumRegionFormat regionFormat = EnumRegionFormat.MCA;
    public static @DoNotLoad BufferedLinearRegionFileFlusher blinearFlusher = null;

    public static boolean isReadOnlyMode() {
        return LeafConstants.LINEAR_V2_READ_ONLY && regionFormat == EnumRegionFormat.LINEAR_V2;
    }

    @Override
    public void onLoaded() {
        regionFormat = EnumRegionFormat.fromString(regionFormatName);
        if (regionFormat == EnumRegionFormat.UNKNOWN) {
            LeafConfig.LOGGER.error("Unknown region format type {}! Falling back to MCA format.", regionFormatName);
            regionFormat = EnumRegionFormat.MCA;
            return;
        }

        if (regionFormat == EnumRegionFormat.LINEAR_V2) {
            checkCompressionLevel();
            LeafConfig.LOGGER.warn("Linear v2 region format is unstable and not recommended to use, beware of data loss and take backups.");
            if (isReadOnlyMode()) {
                LeafConfig.LOGGER.error("============================================================");
                LeafConfig.LOGGER.error("                  LINEAR_V2 READ-ONLY MODE                 ");
                LeafConfig.LOGGER.error("============================================================");
                LeafConfig.LOGGER.error("Linear v2 read-only mode is enabled.");
                LeafConfig.LOGGER.error("Any world changes in Linear v2 regions will NOT be saved.");
                LeafConfig.LOGGER.error("Chunk, entity, player data and POI changes will be discarded.");
                LeafConfig.LOGGER.error("This mode is intended for inspection, testing, migration, or emergency recovery.");
                LeafConfig.LOGGER.error("To enable LINEAR_V2 writing, stop the server, take backups,");
                LeafConfig.LOGGER.error("then remove the JVM flag: -D{}=true", LeafConstants.LINEAR_V2_READ_ONLY_FLAG);
                LeafConfig.LOGGER.error("============================================================");
            }
            LinearRegionFile.SAVE_DELAY_MS = ioFlushDelay <= 0 ? 100 : ioFlushDelay;
            LinearRegionFile.SAVE_THREAD_MAX_COUNT = ioThreadCount;
            LinearRegionFile.USE_VIRTUAL_THREAD = linearUseVirtualThread;
        }

        if (regionFormat == EnumRegionFormat.B_LINEAR) {
            final int ioFlushDelay = RegionFormatConfig.ioFlushDelay <= 0 ? 3000 : RegionFormatConfig.ioFlushDelay;
            blinearFlusher = new BufferedLinearRegionFileFlusher(ioThreadCount, 20, ioFlushDelay);

            checkCompressionLevel();

            // we don't need to consider that it will be reloaded more than once as this config is unreloadable
            Runtime.getRuntime().addShutdownHook(new Thread(() -> blinearFlusher.shutdown()));
        }
    }

    private static void checkCompressionLevel() {
        if (compressionLevel > 22 || compressionLevel < 1) {
            LeafConfig.LOGGER.error("Linear or BufferedLinear region compression level should be between 1 and 22, but got {} in config", compressionLevel);
            LeafConfig.LOGGER.error("Falling back to compression level 1.");
            compressionLevel = 1;
        }
    }
}
