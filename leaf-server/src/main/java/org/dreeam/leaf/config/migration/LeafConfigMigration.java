package org.dreeam.leaf.config.migration;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.util.ConfigVersion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes module-declared path migrations against raw Leaf configuration files.
 *
 * <p>The declarations are checked by tests, not at runtime (see {@code LeafConfigMigrationTest}): a
 * source must not have two different targets, paths of different operations in one file must not
 * overlap, and operations that depend on each other must be declared in the order they must run. Only
 * what comes from the configuration files themselves is checked here.</p>
 */
public final class LeafConfigMigration {

    private static final Logger LOGGER = LogManager.getLogger(LeafConfigMigration.class.getSimpleName());

    private LeafConfigMigration() {
    }

    /**
     * Applies the migrations the global configuration and the world defaults still need. Each file has its own
     * version, and a migration is decided by the version of the file it moves an option from. Operations
     * that start in the global file run first, then the ones that start in the world defaults.
     */
    public static void migrate(
        ConfigFile globalConfig,
        ConfigFile worldDefaultsConfig,
        Iterable<Class<?>> moduleClasses,
        ConfigVersion globalVersion,
        ConfigVersion worldDefaultsVersion
    ) throws Exception {
        List<ConfigMigrationContext.Operation> operations = new ArrayList<>();
        for (ConfigMigrationContext.Operation operation : collect(globalVersion, moduleClasses).operations()) {
            if (operation.source() == ConfigMigrationContext.Scope.GLOBAL) {
                operations.add(operation);
            }
        }
        for (ConfigMigrationContext.Operation operation : collect(worldDefaultsVersion, moduleClasses).operations()) {
            if (operation.source() == ConfigMigrationContext.Scope.WORLD_DEFAULTS) {
                operations.add(operation);
            }
        }
        apply(operations, globalConfig, worldDefaultsConfig);
    }

    /**
     * Applies the migrations a world override still needs, according to the version it was written with.
     *
     * @return whether the override was changed
     */
    public static boolean migrateWorldOverride(
        ConfigFile worldOverrideConfig,
        ConfigVersion storedVersion,
        Iterable<Class<?>> worldModuleClasses
    ) throws Exception {
        ConfigMigrationContext context = collect(storedVersion, worldModuleClasses);
        List<ConfigMigrationContext.Operation> operations = new ArrayList<>();
        for (ConfigMigrationContext.Operation operation : context.operations()) {
            if (operation.source() == ConfigMigrationContext.Scope.WORLD_DEFAULTS
                && operation.target() == ConfigMigrationContext.Scope.WORLD_DEFAULTS) {
                operations.add(operation);
            } else if (operation.source() == ConfigMigrationContext.Scope.WORLD_DEFAULTS
                && operation.target() == ConfigMigrationContext.Scope.GLOBAL
                && worldOverrideConfig.contains(operation.oldPath())) {
                // The option moved to the global configuration, where a world cannot override it.
                LOGGER.warn("{} sets {}, which is no longer a per-world option and is ignored. It moved to {} in the global configuration.",
                    worldOverrideConfig.getFile(), operation.oldPath(), operation.newPath());
            }
        }
        return apply(operations, worldOverrideConfig, worldOverrideConfig);
    }

    static ConfigMigrationContext collect(ConfigVersion storedVersion, Iterable<Class<?>> moduleClasses) throws Exception {
        ConfigMigrationContext context = new ConfigMigrationContext(storedVersion);
        for (Class<?> moduleClass : moduleClasses) {
            Method method;
            try {
                method = moduleClass.getDeclaredMethod("migrate", ConfigMigrationContext.class);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            method.setAccessible(true);
            try {
                method.invoke(null, context);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception checked) throw checked;
                if (cause instanceof Error error) throw error;
                throw exception;
            }
        }
        return context;
    }

    private static boolean apply(
        List<ConfigMigrationContext.Operation> operations,
        ConfigFile globalConfig,
        ConfigFile worldDefaultsConfig
    ) {
        boolean changed = false;
        for (ConfigMigrationContext.Operation operation : operations) {
            ConfigFile source = config(operation.source(), globalConfig, worldDefaultsConfig);
            if (!source.contains(operation.oldPath())) continue;
            Object oldValue = source.get(operation.oldPath());
            if (oldValue == null || oldValue instanceof ConfigSection) {
                throw new IllegalStateException("Legacy config path must point to an option: " + operation.oldPath()
                    + " in " + source.getFile());
            }

            ConfigFile target = config(operation.target(), globalConfig, worldDefaultsConfig);
            Object newValue = target.get(operation.newPath());
            if (newValue instanceof ConfigSection) {
                throw new IllegalStateException("Migration target must point to an option: " + operation.newPath()
                    + " in " + target.getFile());
            }
            if (newValue == null) {
                target.set(operation.newPath(), oldValue);
            } else if (!Objects.equals(String.valueOf(newValue), String.valueOf(oldValue))) {
                // The new option may be a choice the administrator made, so it wins. Dropping a different old
                // value is only worth a warning, an old value that is the same as the new one is not.
                LOGGER.warn("Both {} {} and {} {} are set; keeping the new option's value {} and dropping the old value {}.",
                    operation.source(), operation.oldPath(), operation.target(), operation.newPath(), newValue, oldValue);
            }
            source.set(operation.oldPath(), null);
            changed = true;
        }
        return changed;
    }

    private static ConfigFile config(
        ConfigMigrationContext.Scope scope,
        ConfigFile globalConfig,
        ConfigFile worldDefaultsConfig
    ) {
        return scope == ConfigMigrationContext.Scope.GLOBAL ? globalConfig : worldDefaultsConfig;
    }
}
