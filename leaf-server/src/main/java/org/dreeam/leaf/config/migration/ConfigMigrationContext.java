package org.dreeam.leaf.config.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dreeam.leaf.config.LeafConfig;

/**
 * Collects developer-declared Leaf configuration path migrations before configuration binding.
 *
 * <p>A module may declare {@code public static void migrate(ConfigMigrationContext migrations)}.
 * Its method must only register migrations; the framework validates and applies all registered
 * operations to the raw in-memory files before any module default value is read or written.</p>
 */
public final class ConfigMigrationContext {

    private final String storedVersion;
    private final List<Operation> operations = new ArrayList<>();

    ConfigMigrationContext(String storedVersion) {
        this.storedVersion = storedVersion;
    }

    public boolean isBefore(String version) {
        return this.storedVersion == null
            ? LeafConfig.isConfigVersionBefore(version)
            : LeafConfig.isConfigVersionBefore(this.storedVersion, version);
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
