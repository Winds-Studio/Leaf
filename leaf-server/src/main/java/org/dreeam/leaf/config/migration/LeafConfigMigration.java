package org.dreeam.leaf.config.migration;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes module-declared path migrations against raw Leaf configuration files. */
public final class LeafConfigMigration {

    private LeafConfigMigration() {
    }

    public static void migrate(
        ConfigFile globalConfig,
        ConfigFile worldDefaultsConfig,
        Iterable<Class<?>> moduleClasses
    ) throws Exception {
        ConfigMigrationContext context = collect(globalConfig.getString("config-version", null), moduleClasses);
        apply(context.operations(), globalConfig, worldDefaultsConfig);
    }

    public static boolean migrateWorldOverride(
        ConfigFile worldOverrideConfig,
        Iterable<Class<?>> worldModuleClasses
    ) throws Exception {
        ConfigMigrationContext context = collect(null, worldModuleClasses);
        List<ConfigMigrationContext.Operation> operations = new ArrayList<>();
        for (ConfigMigrationContext.Operation operation : context.operations()) {
            if (operation.source() == ConfigMigrationContext.Scope.WORLD_DEFAULTS
                && operation.target() == ConfigMigrationContext.Scope.WORLD_DEFAULTS) {
                operations.add(operation);
            }
        }
        return apply(operations, worldOverrideConfig, worldOverrideConfig);
    }

    private static ConfigMigrationContext collect(String storedVersion, Iterable<Class<?>> moduleClasses) throws Exception {
        ConfigMigrationContext context = new ConfigMigrationContext(storedVersion);
        for (Class<?> moduleClass : moduleClasses) {
            Method method;
            try {
                method = moduleClass.getDeclaredMethod("migrate", ConfigMigrationContext.class);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
                throw new IllegalStateException("Invalid Leaf config migration method: " + moduleClass.getName());
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
    ) throws Exception {
        Map<String, ConfigMigrationContext.Operation> targets = new HashMap<>();
        for (ConfigMigrationContext.Operation operation : operations) {
            validate(operation);
            ConfigFile source = config(operation.source(), globalConfig, worldDefaultsConfig);
            if (source.contains(operation.oldPath())) {
                Object value = source.get(operation.oldPath());
                if (value == null || value instanceof ConfigSection) {
                    throw new IllegalStateException("Legacy config path must point to an option: " + operation.oldPath());
                }
            }
            ConfigFile target = config(operation.target(), globalConfig, worldDefaultsConfig);
            if (target.contains(operation.newPath()) && target.get(operation.newPath()) instanceof ConfigSection) {
                throw new IllegalStateException("Migration target must point to an option: " + operation.newPath());
            }
            String targetKey = operation.target() + ":" + operation.newPath();
            ConfigMigrationContext.Operation previous = targets.putIfAbsent(targetKey, operation);
            if (previous != null && (previous.source() != operation.source()
                || !previous.oldPath().equals(operation.oldPath()))) {
                throw new IllegalArgumentException("Multiple Leaf migrations target the same option: " + targetKey);
            }
        }
        boolean changed = false;
        Map<ConfigFile, Map<String, PreviousValue>> previousValues = new IdentityHashMap<>();
        for (ConfigMigrationContext.Operation operation : operations) {
            snapshot(previousValues, config(operation.source(), globalConfig, worldDefaultsConfig), operation.oldPath());
            snapshot(previousValues, config(operation.target(), globalConfig, worldDefaultsConfig), operation.newPath());
        }
        try {
            for (ConfigMigrationContext.Operation operation : operations) {
                ConfigFile source = config(operation.source(), globalConfig, worldDefaultsConfig);
                if (!source.contains(operation.oldPath())) continue;
                ConfigFile target = config(operation.target(), globalConfig, worldDefaultsConfig);
                if (!target.contains(operation.newPath())) {
                    target.set(operation.newPath(), source.get(operation.oldPath()));
                }
                source.set(operation.oldPath(), null);
                changed = true;
            }
        } catch (RuntimeException exception) {
            restore(previousValues);
            throw exception;
        }
        return changed;
    }

    private static void snapshot(Map<ConfigFile, Map<String, PreviousValue>> values, ConfigFile config, String path) {
        values.computeIfAbsent(config, ignored -> new java.util.LinkedHashMap<>())
            .putIfAbsent(path, new PreviousValue(config.contains(path), config.get(path)));
    }

    private static void restore(Map<ConfigFile, Map<String, PreviousValue>> values) {
        for (Map.Entry<ConfigFile, Map<String, PreviousValue>> configValues : values.entrySet()) {
            for (Map.Entry<String, PreviousValue> value : configValues.getValue().entrySet()) {
                configValues.getKey().set(value.getKey(), value.getValue().present() ? value.getValue().value() : null);
            }
        }
    }

    private static void validate(ConfigMigrationContext.Operation operation) {
        requirePath(operation.oldPath(), "oldPath");
        requirePath(operation.newPath(), "newPath");
        if (operation.source() == operation.target() && operation.oldPath().equals(operation.newPath())) {
            throw new IllegalArgumentException("A migration must change the file or configuration path");
        }
        if (operation.source() == operation.target()
            && (operation.oldPath().startsWith(operation.newPath() + ".")
            || operation.newPath().startsWith(operation.oldPath() + "."))) {
            throw new IllegalArgumentException("Migration paths in one configuration file must not overlap");
        }
    }

    private static void requirePath(String path, String name) {
        Objects.requireNonNull(path, name);
        if (path.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static ConfigFile config(
        ConfigMigrationContext.Scope scope,
        ConfigFile globalConfig,
        ConfigFile worldDefaultsConfig
    ) {
        return scope == ConfigMigrationContext.Scope.GLOBAL ? globalConfig : worldDefaultsConfig;
    }

    private record PreviousValue(boolean present, Object value) {
    }
}
