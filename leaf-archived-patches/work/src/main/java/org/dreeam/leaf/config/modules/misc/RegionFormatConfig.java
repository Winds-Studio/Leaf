package org.dreeam.leaf.config.modules.misc;

import com.mojang.logging.LogUtils;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.slf4j.Logger;
import org.stupidcraft.linearpaper.region.EnumRegionFileExtension;

public class RegionFormatConfig extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".region-format-settings";
    }

    @DoNotLoad
    private static final Logger logger = LogUtils.getLogger();
    @DoNotLoad
    public static EnumRegionFileExtension regionFormatType;

    public static String regionFormatTypeName = "MCA";
    public static int linearCompressionLevel = 1;
    public static boolean throwOnUnknownExtension = false;
    public static int linearFlushFrequency = 5;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Linear is a region file format that uses ZSTD compression instead of
                ZLIB.
                This format saves about 50% of disk space.
                Read Documentation before using: https://github.com/xymb-endcrystalme/LinearRegionFileFormatTools
                Disclaimer: This is an experimental feature, there is potential risk to lose chunk data.
                So backup your server before switching to Linear.""",
            """
                Linear 是一种使用 ZSTD 压缩而非 ZLIB 的区域文件格式.
                该格式可节省约 50% 的磁盘空间.
                使用前请阅读文档: https://github.com/xymb-endcrystalme/LinearRegionFileFormatTools
                免责声明: 实验性功能,有可能导致区块数据丢失.
                切换到Linear前请备份服务器.""");

        regionFormatTypeName = config.getString(getBasePath() + ".region-format", regionFormatTypeName,
            config.pickStringRegionBased(
                "Available region formats: MCA, LINEAR",
                "可用格式: MCA, LINEAR"));
        linearCompressionLevel = config.getInt(getBasePath() + ".linear-compress-level", linearCompressionLevel);
        throwOnUnknownExtension = config.getBoolean(getBasePath() + ".throw-on-unknown-extension-detected", throwOnUnknownExtension);
        linearFlushFrequency = config.getInt(getBasePath() + ".flush-interval-seconds", linearFlushFrequency);

        regionFormatType = EnumRegionFileExtension.fromName(regionFormatTypeName);
        if (regionFormatType == EnumRegionFileExtension.UNKNOWN) {
            logger.error("Unknown region file type {} ! Falling back to MCA format.", regionFormatTypeName);
            regionFormatType = EnumRegionFileExtension.MCA;
        }

        if (linearCompressionLevel > 23 || linearCompressionLevel < 1) {
            logger.error("Linear region compression level should be between 1 and 22 in config: {}", linearCompressionLevel);
            logger.error("Falling back to compression level 1.");
            linearCompressionLevel = 1;
        }
    }
}
