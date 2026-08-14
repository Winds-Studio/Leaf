package org.dreeam.leaf.config.migration;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.util.Objects;

/**
 * Applies versioned migrations to the loaded Leaf configuration instances before defaults are added.
 */
public final class LeafConfigMigration {

    private LeafConfigMigration() {
    }

    public static void migrate(ConfigFile globalConfig, ConfigFile worldDefaultsConfig) throws Exception {
        String storedVersion = globalConfig.getString("config-version", null);
        MigrationContext context = new MigrationContext(globalConfig, worldDefaultsConfig);

        applyMigrations(storedVersion, context);
    }

    private static void applyMigrations(String storedVersion, MigrationContext context) throws Exception {
        /*
         * Add migrations here, grouped by the config version that introduced the new path.
         *
         * if (LeafConfig.isConfigVersionBefore(storedVersion, "3.1")) {
         *     context.migrate(
         *         ConfigFileType.GLOBAL, "old.path",
         *         ConfigFileType.WORLD_DEFAULTS, "new.path"
         *     );
         * }
         */
    }

    private enum ConfigFileType {
        GLOBAL,
        WORLD_DEFAULTS
    }

    private static final class MigrationContext {

        private final ConfigFile globalConfig;
        private final ConfigFile worldDefaultsConfig;

        private MigrationContext(ConfigFile globalConfig, ConfigFile worldDefaultsConfig) {
            this.globalConfig = globalConfig;
            this.worldDefaultsConfig = worldDefaultsConfig;
        }

        private void migrate(
            ConfigFileType source,
            String oldPath,
            ConfigFileType target,
            String newPath
        ) throws Exception {
            validateMigration(source, oldPath, target, newPath);

            ConfigFile sourceConfig = config(source);
            if (!sourceConfig.contains(oldPath)) {
                return;
            }

            Object oldValue = sourceConfig.get(oldPath);
            if (oldValue == null || oldValue instanceof ConfigSection) {
                throw new IllegalStateException("Legacy config path must point to an option: "
                    + source + ":" + oldPath);
            }

            ConfigFile targetConfig = config(target);
            if (targetConfig.contains(newPath)) {
                sourceConfig.set(oldPath, null);
            } else {
                sourceConfig.moveTo(oldPath, newPath, targetConfig);
            }
        }

        private static void validateMigration(
            ConfigFileType source,
            String oldPath,
            ConfigFileType target,
            String newPath
        ) {
            Objects.requireNonNull(source, "source");
            requirePath(oldPath, "oldPath");
            Objects.requireNonNull(target, "target");
            requirePath(newPath, "newPath");

            if (source == target && oldPath.equals(newPath)) {
                throw new IllegalArgumentException("A migration must change the file or config path");
            }
            if (source == target
                && (oldPath.startsWith(newPath + ".") || newPath.startsWith(oldPath + "."))) {
                throw new IllegalArgumentException("Paths in the same config file must not overlap");
            }
        }

        private static void requirePath(String path, String name) {
            Objects.requireNonNull(path, name);
            if (path.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }

        private ConfigFile config(ConfigFileType type) {
            return type == ConfigFileType.GLOBAL ? this.globalConfig : this.worldDefaultsConfig;
        }
    }
}
