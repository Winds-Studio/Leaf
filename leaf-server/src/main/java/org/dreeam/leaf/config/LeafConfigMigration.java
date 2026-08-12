package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.Objects;

/**
 * Applies versioned migrations to raw Leaf configuration files before defaults are added.
 */
final class LeafConfigMigration {

    private LeafConfigMigration() {
    }

    static void migrate(File globalFile, File worldDefaultsFile) throws Exception {
        if (!globalFile.isFile()) {
            return;
        }

        ConfigFile globalConfig = ConfigFile.loadConfig(globalFile);
        String storedVersion = globalConfig.getString("config-version", null);
        MigrationContext context = new MigrationContext(globalConfig, worldDefaultsFile);

        applyMigrations(storedVersion, context);

        context.saveChanges();
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
        private final File worldDefaultsFile;
        private ConfigFile worldDefaultsConfig;
        private boolean globalChanged;
        private boolean worldDefaultsChanged;

        private MigrationContext(ConfigFile globalConfig, File worldDefaultsFile) {
            this.globalConfig = globalConfig;
            this.worldDefaultsFile = worldDefaultsFile;
        }

        private void migrate(
            ConfigFileType source,
            String oldPath,
            ConfigFileType target,
            String newPath
        ) throws Exception {
            validateMigration(source, oldPath, target, newPath);

            ConfigFile sourceConfig = config(source, false);
            if (sourceConfig == null || !sourceConfig.contains(oldPath)) {
                return;
            }

            Object oldValue = sourceConfig.get(oldPath);
            if (oldValue == null || oldValue instanceof ConfigSection) {
                throw new IllegalStateException("Legacy config path must point to an option: "
                    + source + ":" + oldPath);
            }

            ConfigFile targetConfig = config(target, true);
            if (targetConfig.contains(newPath)) {
                sourceConfig.set(oldPath, null);
            } else {
                sourceConfig.moveTo(oldPath, newPath, targetConfig);
            }

            markChanged(source);
            markChanged(target);
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

        private ConfigFile config(ConfigFileType type, boolean create) throws Exception {
            if (type == ConfigFileType.GLOBAL) {
                return this.globalConfig;
            }
            if (this.worldDefaultsConfig != null) {
                return this.worldDefaultsConfig;
            }
            if (!create && !this.worldDefaultsFile.isFile()) {
                return null;
            }
            this.worldDefaultsConfig = ConfigFile.loadConfig(this.worldDefaultsFile);
            return this.worldDefaultsConfig;
        }

        private void markChanged(ConfigFileType type) {
            if (type == ConfigFileType.GLOBAL) {
                this.globalChanged = true;
            } else {
                this.worldDefaultsChanged = true;
            }
        }

        private void saveChanges() throws Exception {
            if (this.globalChanged) {
                this.globalConfig.save();
            }
            if (this.worldDefaultsChanged) {
                this.worldDefaultsConfig.save();
            }
        }
    }
}
