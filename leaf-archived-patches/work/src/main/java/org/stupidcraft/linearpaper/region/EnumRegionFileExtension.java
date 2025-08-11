package org.stupidcraft.linearpaper.region;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum EnumRegionFileExtension {
    LINEAR(".linear"),
    MCA(".mca"),
    UNKNOWN(null);

    private final String extensionName;

    EnumRegionFileExtension(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getExtensionName() {
        return this.extensionName;
    }

    @Contract(pure = true)
    public static EnumRegionFileExtension fromName(@NotNull String name) {
        switch (name.toUpperCase(Locale.ROOT)) {
            case "MCA" -> {
                return MCA;
            }

            case "LINEAR" -> {
                return LINEAR;
            }
            default -> {
                return UNKNOWN;
            }

        }
    }

    @Contract(pure = true)
    public static EnumRegionFileExtension fromExtension(@NotNull String name) {
        switch (name.toLowerCase()) {
            case "mca" -> {
                return MCA;
            }

            case "linear" -> {
                return LINEAR;
            }

            default -> {
                return UNKNOWN;
            }
        }
    }
}
