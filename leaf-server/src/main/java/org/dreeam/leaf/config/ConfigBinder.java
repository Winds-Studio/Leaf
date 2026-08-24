package org.dreeam.leaf.config;

import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;
import org.dreeam.leaf.config.util.ConfigPaths;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds annotation-driven module fields to a global or world configuration view.
 */
final class ConfigBinder {

    // Static fields no longer expose their code defaults after the first successful bind.
    private static final Map<Field, Object> GLOBAL_DEFAULT_VALUES = new HashMap<>();

    static void registerGlobalDefaults(Object module) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        for (Field field : moduleClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigInfo.class) == null
                || field.isAnnotationPresent(DoNotLoad.class)) {
                continue;
            }

            validateField(moduleClass, field, true);
            field.setAccessible(true);
            Object defaultValue = field.get(null);
            if (defaultValue == null) {
                throw new IllegalStateException("Configuration field has a null default value: " + field);
            }
            GLOBAL_DEFAULT_VALUES.putIfAbsent(field, copyValue(defaultValue));
        }
    }

    static void collectGlobalReload(
        Object module,
        LeafConfigAccessor config,
        List<PendingValue> pendingValues
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        ConfigClassInfo classInfo = moduleClass.getAnnotation(ConfigClassInfo.class);
        if (classInfo == null) {
            throw new IllegalStateException("Configuration module " + moduleClass.getName()
                + " is missing @ConfigClassInfo");
        }

        String basePath = ConfigPaths.modulePath(moduleClass);
        String sectionComment = config.pickStringRegionBased(classInfo.comments());
        if (sectionComment != null) {
            config.addComment(basePath, sectionComment);
        }

        boolean skipModuleReload = moduleClass.isAnnotationPresent(HotReloadUnsupported.class);

        for (Field field : moduleClass.getDeclaredFields()) {
            ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);
            if (configInfo == null
                || field.isAnnotationPresent(DoNotLoad.class)) {
                continue;
            }

            validateField(moduleClass, field, true);
            field.setAccessible(true);

            String path = ConfigPaths.fieldPath(moduleClass, field);
            Object defaultValue = globalDefaultValue(field);

            String comment = config.pickStringRegionBased(configInfo.comments());
            Object loadedValue = readValue(config, path, comment, field, defaultValue, true);
            if (!skipModuleReload && !field.isAnnotationPresent(HotReloadUnsupported.class)) {
                pendingValues.add(new PendingValue(null, field, loadedValue));
            }
        }
    }

    static void collectWorldReload(
        Object module,
        Object loadedModule,
        List<PendingValue> pendingValues
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        if (loadedModule.getClass() != moduleClass) {
            throw new IllegalArgumentException("Configuration modules must have the same type");
        }
        if (moduleClass.isAnnotationPresent(HotReloadUnsupported.class)) {
            return;
        }

        for (Field field : moduleClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigInfo.class) == null
                || field.isAnnotationPresent(DoNotLoad.class)
                || field.isAnnotationPresent(HotReloadUnsupported.class)) {
                continue;
            }

            validateField(moduleClass, field, false);
            field.setAccessible(true);
            pendingValues.add(new PendingValue(module, field, field.get(loadedModule)));
        }
    }

    public static void bind(
        Object module,
        @Nullable Object worldDefaultModule,
        LeafConfigAccessor config,
        boolean global
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        ConfigClassInfo classInfo = moduleClass.getAnnotation(ConfigClassInfo.class);
        if (classInfo == null) {
            throw new IllegalStateException("Configuration module " + moduleClass.getName()
                + " is missing @ConfigClassInfo");
        }

        String basePath = ConfigPaths.modulePath(moduleClass);
        String sectionComment = config.pickStringRegionBased(classInfo.comments());
        if (sectionComment != null && (!(config instanceof LeafWorldConfig worldConfig) || worldConfig.isWorldDefaults())) {
            config.addComment(basePath, sectionComment);
        }

        for (Field field : moduleClass.getDeclaredFields()) {
            boolean skipLoad = field.getAnnotation(DoNotLoad.class) != null;
            ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);

            if (skipLoad || configInfo == null) {
                continue;
            }

            if (global) {
                bindGlobal(moduleClass, field, configInfo, config);
            } else {
                bindWorld(module, worldDefaultModule, moduleClass, field, configInfo, config);
            }
        }
    }

    private static void bindGlobal(
        Class<?> moduleClass,
        Field field,
        ConfigInfo configInfo,
        LeafConfigAccessor config
    ) throws IllegalAccessException {
        validateField(moduleClass, field, true);
        field.setAccessible(true);

        String path = ConfigPaths.fieldPath(moduleClass, field);
        Object defaultValue = globalDefaultValue(field);

        String comment = config.pickStringRegionBased(configInfo.comments());
        Object loadedValue = readValue(config, path, comment, field, defaultValue, true);
        field.set(null, loadedValue);
    }

    private static void bindWorld(
        Object module,
        @Nullable Object defaultsModule,
        Class<?> moduleClass,
        Field field,
        ConfigInfo configInfo,
        LeafConfigAccessor config
    ) throws IllegalAccessException {
        validateField(moduleClass, field, false);
        field.setAccessible(true);

        String path = ConfigPaths.fieldPath(moduleClass, field);
        Object defaultValue = defaultsModule == null
            ? field.get(module) // World default
            : copyValue(field.get(defaultsModule)); // World override copy from defaults
        if (defaultValue == null) {
            throw new IllegalStateException("Configuration field has a null default value: " + field);
        }

        boolean worldOverridden = defaultsModule != null;
        if (worldOverridden && !config.contains(path)) {
            // Use world default if no override path defined
            field.set(module, defaultValue);
            return;
        }

        String comment = config.pickStringRegionBased(configInfo.comments());
        Object loadedValue = readValue(config, path, comment, field, defaultValue, !worldOverridden);
        field.set(module, loadedValue);
    }

    static void applyWorldDefaults(
        Object module,
        Object defaultsModule
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        if (defaultsModule.getClass() != moduleClass) {
            throw new IllegalArgumentException("Configuration modules must have the same type");
        }
        for (Field field : moduleClass.getDeclaredFields()) {
            if (field.getAnnotation(DoNotLoad.class) != null
                || field.getAnnotation(ConfigInfo.class) == null) {
                continue;
            }

            validateField(moduleClass, field, false);
            field.setAccessible(true);
            field.set(module, copyValue(field.get(defaultsModule)));
        }
    }

    // TODO[To-GitHub-issue]: Not sure whether needs to validate, we don't expose LeafConfig as public framework
    private static void validateField(Class<?> moduleClass, Field field, boolean global) {
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)) {
            throw new IllegalStateException("@ConfigInfo field must be mutable: "
                + moduleClass.getName() + "." + field.getName());
        }
        if (Modifier.isStatic(modifiers) != global) {
            String expected = global ? "static" : "an instance field";
            throw new IllegalStateException("@ConfigInfo field must be " + expected + ": "
                + moduleClass.getName() + "." + field.getName());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readValue(
        LeafConfigAccessor config,
        String path,
        @Nullable String comment,
        Field field,
        Object defaultValue,
        boolean writeDefault
    ) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            if (!writeDefault) {
                return config.configFile.getBoolean(path, (Boolean) defaultValue);
            }
            return comment == null
                ? config.getBoolean(path, (Boolean) defaultValue)
                : config.getBoolean(path, (Boolean) defaultValue, comment);
        }
        if (type == int.class || type == Integer.class) {
            if (!writeDefault) {
                return config.configFile.getInteger(path, (Integer) defaultValue);
            }
            return comment == null
                ? config.getInt(path, (Integer) defaultValue)
                : config.getInt(path, (Integer) defaultValue, comment);
        }
        if (type == long.class || type == Long.class) {
            if (!writeDefault) {
                return config.configFile.getLong(path, (Long) defaultValue);
            }
            return comment == null
                ? config.getLong(path, (Long) defaultValue)
                : config.getLong(path, (Long) defaultValue, comment);
        }
        if (type == double.class || type == Double.class) {
            if (!writeDefault) {
                return config.configFile.getDouble(path, (Double) defaultValue);
            }
            return comment == null
                ? config.getDouble(path, (Double) defaultValue)
                : config.getDouble(path, (Double) defaultValue, comment);
        }
        if (type == String.class) {
            if (!writeDefault) {
                return config.configFile.getString(path, (String) defaultValue);
            }
            return comment == null
                ? config.getString(path, (String) defaultValue)
                : config.getString(path, (String) defaultValue, comment);
        }
        if (List.class.isAssignableFrom(type)) {
            if (!writeDefault) {
                return config.configFile.getStringList(path);
            }
            return comment == null
                ? config.getList(path, (List<String>) defaultValue)
                : config.getList(path, (List<String>) defaultValue, comment);
        }
        if (type.isEnum()) {
            String value;
            if (!writeDefault) {
                value = config.configFile.getString(path, ((Enum<?>) defaultValue).name());
            } else {
                value = comment == null
                    ? config.getString(path, ((Enum<?>) defaultValue).name())
                    : config.getString(path, ((Enum<?>) defaultValue).name(), comment);
            }
            try {
                return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid value '" + value + "' for " + path
                    + "; expected one of " + List.of(type.getEnumConstants()), exception);
            }
        }
        throw new IllegalArgumentException("Unsupported @ConfigInfo field type: " + field);
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    private static Object globalDefaultValue(Field field) {
        Object defaultValue = GLOBAL_DEFAULT_VALUES.get(field);
        if (defaultValue == null) {
            throw new IllegalStateException("Code default was not registered for configuration field: " + field);
        }
        return copyValue(defaultValue);
    }

    static final class PendingValue {

        private final @Nullable Object target;
        private final Field field;
        private final Object value;
        private final Object previousValue;

        private PendingValue(@Nullable Object target, Field field, Object value) throws IllegalAccessException {
            this.target = target;
            this.field = field;
            this.value = copyValue(value);
            this.previousValue = copyValue(field.get(target));
        }

        void apply() throws IllegalAccessException {
            this.field.set(this.target, copyValue(this.value));
        }

        void restore() throws IllegalAccessException {
            this.field.set(this.target, copyValue(this.previousValue));
        }
    }
}
