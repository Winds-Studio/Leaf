package me.earthme.luminol.enums;

import abomination.LinearRegionFile;
import me.earthme.luminol.data.BufferedLinearRegionFile;
import me.earthme.luminol.utils.IRegionCreateFunction;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.dreeam.leaf.config.modules.misc.RegionFormatConfig;

public enum EnumRegionFormat {
    MCA("mca", "mca", info -> new RegionFile(info.info(), info.filePath(), info.folder(), info.sync())),
    LINEAR_V2("linear_v2", "linear", info -> new LinearRegionFile(info.info(), info.filePath(), info.folder(), info.sync(), RegionFormatConfig.compressionLevel)),
    B_LINEAR("b_linear", "b_linear", info -> new BufferedLinearRegionFile(info.filePath(), RegionFormatConfig.compressionLevel, RegionFormatConfig.blinearFlusher)),
    UNKNOWN("unknown", "mca", info -> null);

    private final String name;
    private final String extensionName;
    private final IRegionCreateFunction creator;

    EnumRegionFormat(String name, String extensionName, IRegionCreateFunction creator) {
        this.name = name;
        this.extensionName = extensionName;
        this.creator = creator;
    }

    public static EnumRegionFormat fromString(String string) {
        for (EnumRegionFormat format : values()) {
            if (format.name.equalsIgnoreCase(string)) {
                return format;
            }
        }

        return UNKNOWN;
    }

    public IRegionCreateFunction getCreator() {
        return this.creator;
    }

    public String getExtensionName() {
        return this.extensionName;
    }
}
