package org.dreeam.leaf.config;

import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

        String basePath = ConfigPathResolver.modulePath(moduleClass);
        String sectionComment = config.pickStringRegionBased(classInfo.comments());
        if (sectionComment != null && (!(config instanceof LeafWorldConfig worldConfig) || worldConfig.isDefaultsConfig())) {
            config.addComment(basePath, sectionComment);
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
            String path = ConfigPathResolver.fieldPath(moduleClass, field);
            Object defaultValue = field.get(target);
            String comment = config.pickStringRegionBased(configInfo.comments());
            // Always call readValue, to keep comments on reloading
            Object loadedValue = readValue(config, path, comment, field, defaultValue);

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readValue(
        LeafConfigAccessor config,
        String path,
        @Nullable String comment,
        Field field,
        @Nullable Object defaultValue
    ) {
        if (defaultValue == null) {
            throw new IllegalStateException("Configuration field has a null default value: " + field);
        }

        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            return comment == null
                ? config.getBoolean(path, (Boolean) defaultValue)
                : config.getBoolean(path, (Boolean) defaultValue, comment);
        }
        if (type == int.class || type == Integer.class) {
            return comment == null
                ? config.getInt(path, (Integer) defaultValue)
                : config.getInt(path, (Integer) defaultValue, comment);
        }
        if (type == long.class || type == Long.class) {
            return comment == null
                ? config.getLong(path, (Long) defaultValue)
                : config.getLong(path, (Long) defaultValue, comment);
        }
        if (type == double.class || type == Double.class) {
            return comment == null
                ? config.getDouble(path, (Double) defaultValue)
                : config.getDouble(path, (Double) defaultValue, comment);
        }
        if (type == String.class) {
            return comment == null
                ? config.getString(path, (String) defaultValue)
                : config.getString(path, (String) defaultValue, comment);
        }
        if (List.class.isAssignableFrom(type)) {
            return comment == null
                ? config.getList(path, (List<String>) defaultValue)
                : config.getList(path, (List<String>) defaultValue, comment);
        }
        if (type.isEnum()) {
            String value = comment == null
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
