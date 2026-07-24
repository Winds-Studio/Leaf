package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.util.Objects;

/**
 * Moves a configuration value from a legacy path to its replacement path.
 */
public final class ConfigPathMigration {

    private ConfigPathMigration() {
    }

    /**
     * Moves the value at {@code oldPath} to {@code newPath} when the old path exists.
     *
     * <p>A successful migration removes the old path, so it is performed only once when the
     * configuration is next saved.</p>
     *
     * @param config the configuration containing both paths
     * @param oldPath the deprecated configuration path
     * @param newPath the replacement configuration path
     * @return {@code true} if a value was moved
     */
    public static boolean migrate(ConfigSection config, String oldPath, String newPath) {
        Objects.requireNonNull(config, "config");
        if (oldPath.equals(newPath)) {
            throw new IllegalArgumentException("The old and new config paths must differ");
        }

        if (!config.contains(oldPath)) {
            return false;
        }
        config.moveTo(oldPath, newPath);
        return true;
    }
}
