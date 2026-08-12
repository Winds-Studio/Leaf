package org.dreeam.leaf.config;

import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Binds annotation-driven module fields to a global or world configuration view.
 */
final class ConfigBinder {

    private ConfigBinder() {
    }

    static void bindGlobal(
        ConfigModule module,
        LeafConfigAccessor config,
        boolean alreadyInitialized
    ) throws IllegalAccessException {
        bind(module, config, true, alreadyInitialized);
    }

    static void bindWorld(
        WorldConfigModule module,
        LeafWorldConfig config,
        boolean alreadyInitialized
    ) throws IllegalAccessException {
        bind(module, config, false, alreadyInitialized);
    }

    private static void bind(
        Object module,
        LeafConfigAccessor config,
        boolean global,
        boolean alreadyInitialized
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        ConfigClassInfo classInfo = moduleClass.getAnnotation(ConfigClassInfo.class);
        if (classInfo == null) {
            throw new IllegalStateException("Configuration module " + moduleClass.getName()
                + " is missing @ConfigClassInfo");
        }

        String basePath = basePath(classInfo);
        if (!classInfo.comments().isBlank()
            && (!(config instanceof LeafWorldConfig worldConfig) || worldConfig.isDefaultsConfig())) {
            config.addComment(basePath, classInfo.comments());
        }

        boolean skipModuleReload = alreadyInitialized
            && moduleClass.isAnnotationPresent(HotReloadUnsupported.class);

        for (Field field : moduleClass.getDeclaredFields()) {
            boolean skipLoad = field.getAnnotation(DoNotLoad.class) != null;
            boolean skipReload = skipModuleReload
                || alreadyInitialized && field.getAnnotation(HotReloadUnsupported.class) != null;
            ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);

            if (skipLoad || configInfo == null) {
                continue;
            }

            validateField(moduleClass, field, global);
            field.setAccessible(true);

            Object target = global ? null : module;
            Object defaultValue = field.get(target);
            // Always call readValue, to keep comments on reloading
            Object loadedValue = readValue(config, path(basePath, configInfo), configInfo.comments(),
                field, defaultValue);

            if (!skipReload) {
                field.set(target, loadedValue);
            }
        }
    }

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

    private static String basePath(ConfigClassInfo info) {
        List<String> path = new ArrayList<>();
        path.add(info.category().basePath());
        path.addAll(List.of(info.directory()));
        path.add(info.name());
        return joinPath(path);
    }

    private static String path(String basePath, ConfigInfo info) {
        List<String> path = new ArrayList<>();
        path.add(basePath);
        path.addAll(List.of(info.directory()));
        path.add(info.name());
        return joinPath(path);
    }

    private static String joinPath(List<String> path) {
        if (path.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException("Configuration path segments must not be blank: " + path);
        }
        return String.join(".", path);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readValue(
        LeafConfigAccessor config,
        String path,
        String comment,
        Field field,
        Object defaultValue
    ) {
        if (defaultValue == null) {
            throw new IllegalStateException("Configuration field has a null default value: " + field);
        }

        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            return comment.isBlank()
                ? config.getBoolean(path, (Boolean) defaultValue)
                : config.getBoolean(path, (Boolean) defaultValue, comment);
        }
        if (type == int.class || type == Integer.class) {
            return comment.isBlank()
                ? config.getInt(path, (Integer) defaultValue)
                : config.getInt(path, (Integer) defaultValue, comment);
        }
        if (type == long.class || type == Long.class) {
            return comment.isBlank()
                ? config.getLong(path, (Long) defaultValue)
                : config.getLong(path, (Long) defaultValue, comment);
        }
        if (type == double.class || type == Double.class) {
            return comment.isBlank()
                ? config.getDouble(path, (Double) defaultValue)
                : config.getDouble(path, (Double) defaultValue, comment);
        }
        if (type == String.class) {
            return comment.isBlank()
                ? config.getString(path, (String) defaultValue)
                : config.getString(path, (String) defaultValue, comment);
        }
        if (List.class.isAssignableFrom(type)) {
            return comment.isBlank()
                ? config.getList(path, (List<String>) defaultValue)
                : config.getList(path, (List<String>) defaultValue, comment);
        }
        if (type.isEnum()) {
            String value = comment.isBlank()
                ? config.getString(path, ((Enum<?>) defaultValue).name())
                : config.getString(path, ((Enum<?>) defaultValue).name(), comment);
            try {
                return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid value '" + value + "' for " + path
                    + "; expected one of " + List.of(type.getEnumConstants()), exception);
            }
        }
        throw new IllegalArgumentException("Unsupported @ConfigInfo field type: " + field);
    }
}
