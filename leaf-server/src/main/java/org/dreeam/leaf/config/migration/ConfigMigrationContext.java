package org.dreeam.leaf.config.migration;

import org.dreeam.leaf.config.util.ConfigVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Collects developer-declared Leaf configuration path migrations before configuration binding.
 *
 * <p>A module may declare {@code public static void migrate(ConfigMigrationContext migrations)}.
 * Its method must only register migrations; the framework applies all registered operations to the raw
 * in-memory files before any module default value is read or written.</p>
 */
public final class ConfigMigrationContext {

    private final ConfigVersion storedVersion;
    private final List<Operation> operations = new ArrayList<>();

    ConfigMigrationContext(ConfigVersion storedVersion) {
        this.storedVersion = Objects.requireNonNull(storedVersion, "storedVersion");
    }

    /**
     * Returns whether the file being migrated was last written by a Leaf version older than {@code version}.
     *
     * @param version a numeric dot-separated config version, such as {@code 3.1}
     * @throws IllegalArgumentException if {@code version} is not a numeric dot-separated version
     */
    public boolean isBefore(String version) {
        return this.storedVersion.compareTo(ConfigVersion.parse(version)) < 0;
    }

    public void migrate(Scope source, String oldPath, Scope target, String newPath) {
        this.operations.add(new Operation(
            Objects.requireNonNull(source, "source"), Objects.requireNonNull(oldPath, "oldPath"),
            Objects.requireNonNull(target, "target"), Objects.requireNonNull(newPath, "newPath")
        ));
    }

    List<Operation> operations() {
        return List.copyOf(this.operations);
    }

    public enum Scope {
        GLOBAL,
        WORLD_DEFAULTS
    }

    record Operation(Scope source, String oldPath, Scope target, String newPath) {
    }
}
