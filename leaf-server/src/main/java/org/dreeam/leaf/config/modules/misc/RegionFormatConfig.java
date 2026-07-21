package org.dreeam.leaf.config.modules.misc;

import abomination.LinearRegionFile;
import me.earthme.luminol.enums.EnumRegionFormat;
import me.earthme.luminol.utils.BufferedLinearRegionFileFlusher;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;
import org.dreeam.leaf.util.LeafConstants;

public class RegionFormatConfig extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".region-format";
    }

    public static @HotReloadUnsupported String regionFormatName = "MCA";
    public static @HotReloadUnsupported int compressionLevel = 6;
    public static @HotReloadUnsupported int ioThreadCount = 6;
    public static @HotReloadUnsupported int ioFlushDelay = -1;
    public static @HotReloadUnsupported boolean linearUseVirtualThread = true;

    public static @DoNotLoad EnumRegionFormat regionFormat = EnumRegionFormat.MCA;
    public static @DoNotLoad BufferedLinearRegionFileFlusher blinearFlusher = null;

    private static boolean regionFormatLoaded = false;

    public static boolean isReadOnlyMode() {
        return LeafConstants.LINEAR_V2_READ_ONLY && regionFormat == EnumRegionFormat.LINEAR_V2;
    }

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Linear is a region format that uses zstd compression instead of zlib.
                This format saves about 50% of disk space.
                Read Leaf docs before using!""",
            """
                Linear 是一种使用 zstd 压缩而非 ZLIB 的区域格式.
                该格式可节省约 50% 的磁盘空间.
                使用前请阅读 Leaf 文档!""");

        if (regionFormatLoaded) {
            config.getConfigSection(getBasePath());
            return;
        }
        regionFormatLoaded = true;

        regionFormatName = config.getString(getBasePath() + ".format-name", regionFormatName,
            config.pickStringRegionBased(
                "Available region format names: MCA, B_LINEAR, LINEAR_V2",
                "可用格式: MCA, B_LINEAR, LINEAR_V2"));
        compressionLevel = config.getInt(getBasePath() + ".compress-level", compressionLevel);
        ioThreadCount = config.getInt(getBasePath() + ".io-thread-count", ioThreadCount);
        ioFlushDelay = config.getInt(getBasePath() + ".io-flush-delay", ioFlushDelay);
        linearUseVirtualThread =  config.getBoolean(getBasePath() + ".linear-use-virtual-thread", linearUseVirtualThread);

        regionFormat = EnumRegionFormat.fromString(regionFormatName);
        if (regionFormat == EnumRegionFormat.UNKNOWN) {
            LOGGER.error("Unknown region format type {}! Falling back to MCA format.", regionFormatName);
            regionFormat = EnumRegionFormat.MCA;
            return;
        }

        if (regionFormat == EnumRegionFormat.LINEAR_V2) {
            checkCompressionLevel();
            LOGGER.warn("Linear v2 region format is unstable and not recommended to use, beware of data loss and take backups.");
            if (isReadOnlyMode()) {
                LOGGER.error("============================================================");
                LOGGER.error("                  LINEAR_V2 READ-ONLY MODE                 ");
                LOGGER.error("============================================================");
                LOGGER.error("Linear v2 read-only mode is enabled.");
                LOGGER.error("Any world changes in Linear v2 regions will NOT be saved.");
                LOGGER.error("Chunk, entity, player data and POI changes will be discarded.");
                LOGGER.error("This mode is intended for inspection, testing, migration, or emergency recovery.");
                LOGGER.error("To enable LINEAR_V2 writing, stop the server, take backups,");
                LOGGER.error("then remove the JVM flag: -D{}=true", LeafConstants.LINEAR_V2_READ_ONLY_FLAG);
                LOGGER.error("============================================================");
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
            LOGGER.error("Linear or BufferedLinear region compression level should be between 1 and 22, but got {} in config", compressionLevel);
            LOGGER.error("Falling back to compression level 1.");
            compressionLevel = 1;
        }
    }
}
