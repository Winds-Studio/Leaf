package org.dreeam.leaf.config.util;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.LeafConfig;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A dot-separated config version such as {@code 3.1}. Components are compared as numbers, missing
 * components count as 0: {@code 3.10 > 3.9} and {@code 3 == 3.0}.
 */
public record ConfigVersion(List<Integer> components) implements Comparable<ConfigVersion> {

    /**
     * Parses a version.
     *
     * @throws IllegalArgumentException if {@code version} is not made of numeric components
     */
    public static ConfigVersion parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Config version must not be blank");
        }

        String[] parts = version.split("\\.", -1);
        List<Integer> components = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(character -> character >= '0' && character <= '9')) {
                throw new IllegalArgumentException("Invalid config version: " + version);
            }
            try {
                components.add(Integer.parseInt(part));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid config version: " + version, exception);
            }
        }
        return new ConfigVersion(List.copyOf(components));
    }

    @Override
    public int compareTo(ConfigVersion other) {
        int componentCount = Math.max(this.components.size(), other.components.size());
        for (int index = 0; index < componentCount; index++) {
            int thisComponent = index < this.components.size() ? this.components.get(index) : 0;
            int otherComponent = index < other.components.size() ? other.components.get(index) : 0;
            int comparison = Integer.compare(thisComponent, otherComponent);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /**
     * Returns whether {@code storedVersion}, a version a configuration was last written with, predates
     * {@code version}. For example, {@code isBefore("3.0", "3.1")} is {@code true}.
     *
     * @param storedVersion a numeric dot-separated config version
     * @param version       a numeric dot-separated config version, such as {@code 3.1}
     * @throws IllegalArgumentException if a version is not a numeric dot-separated version
     */
    public static boolean isBefore(String storedVersion, String version) {
        return parse(storedVersion).compareTo(parse(version)) < 0;
    }

    /**
     * Returns whether {@code storedVersion}, a version a configuration was last written with, is not older
     * than {@code version}.
     *
     * @throws IllegalArgumentException if a version is not a numeric dot-separated version
     */
    public static boolean isAtLeast(String storedVersion, String version) {
        return !isBefore(storedVersion, version);
    }

    /**
     * The version of a configuration file, checked against {@link LeafConfig#CURRENT_CONFIG_VERSION}.
     *
     * @param stored       the version that decides which migrations still have to run
     * @param valueToWrite the {@code config-version} value to save: the current version, or the file's own
     *                     version when it is newer than the supported one
     * @param needsRewrite whether the file has to be saved to bring its version up to date
     */
    public record Check(ConfigVersion stored, Object valueToWrite, boolean needsRewrite) {

        public static Check current() {
            return new Check(parse(LeafConfig.CURRENT_CONFIG_VERSION), LeafConfig.CURRENT_CONFIG_VERSION, false);
        }
    }

    /**
     * The version of a global or world defaults file. Each file records its own migration progress. A file
     * that does not exist yet starts at the current version.
     */
    public static Check checkIfExists(ConfigFile file) {
        return Files.isRegularFile(file.getFile().toPath())
            ? check(file)
            : Check.current();
    }

    /**
     * Reads the {@code config-version} of an existing raw file.
     *
     * <p>A file without a version is read as the current version: it is warned about, not migrated and
     * written back with the version. A version that cannot be parsed is rejected. A version newer than the
     * supported one is warned about, not migrated and keeps its version when saved.</p>
     *
     * @throws IllegalArgumentException if the version cannot be parsed
     */
    public static Check check(ConfigFile file) {
        Check current = Check.current();
        Object raw = file.get(LeafConfig.VERSION_PATH);
        if (raw == null) {
            LeafConfig.LOGGER.warn("{} has no {}; reading it as the current version {} without migrating it. The version is added when the file is saved.",
                file.getFile(), LeafConfig.VERSION_PATH, LeafConfig.CURRENT_CONFIG_VERSION);
            return new Check(current.stored(), LeafConfig.CURRENT_CONFIG_VERSION, true);
        }

        ConfigVersion stored;
        try {
            stored = parse(String.valueOf(raw));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + LeafConfig.VERSION_PATH + " '" + raw + "' in " + file.getFile(), exception);
        }

        int comparison = stored.compareTo(current.stored());
        if (comparison > 0) {
            LeafConfig.LOGGER.warn("{} was written by a newer Leaf version (config-version {}, this server supports up to {}). It is not migrated and keeps its version, options that this version does not know are dropped when it is saved.",
                file.getFile(), raw, LeafConfig.CURRENT_CONFIG_VERSION);
            return new Check(stored, raw, false);
        }
        return new Check(stored, LeafConfig.CURRENT_CONFIG_VERSION, comparison < 0);
    }
}
