package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;
import org.dreeam.leaf.config.util.ConfigPaths;
import org.dreeam.leaf.config.util.ConfigValues;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds annotation-driven module fields to a global or world configuration view.
 *
 * <p>Every bind first reads all values of a module into {@link PendingValue}s and then writes them,
 * so a value that is rejected never leaves a module half applied.</p>
 */
final class ConfigBinder {

    // Values of global @HotReloadUnsupported options, captured once after startup has completed.
    // World values are held by the LeafWorldConfig they belong to, see LeafWorldConfig#initialValues().
    private static final Map<Field, Object> INITIAL_GLOBAL_VALUES = new HashMap<>();

    /** Binds the static fields of a global module during startup. The code defaults are the fallbacks. */
    static void bindGlobal(Object module, LeafConfigAccessor config) throws IllegalAccessException {
        List<PendingValue> values = new ArrayList<>();
        collect(module, null, config, true, null, values);
        applyAll(values);
    }

    /**
     * Reads the values a reload would apply to the static fields of a global module. Missing options keep
     * their current value, and options that cannot be reloaded are restored to their first value.
     */
    static void collectGlobalReload(
        Object module,
        LeafConfigAccessor config,
        List<PendingValue> values
    ) throws IllegalAccessException {
        collect(module, null, config, true, INITIAL_GLOBAL_VALUES, values);
    }

    /**
     * Binds a world defaults module, writing defaults and comments to the configuration file.
     *
     * @param fallbackModule supplies the value of options missing from the file: the module itself when it
     *                       is loaded for the first time, the active module when it is a reload candidate
     */
    static void bindWorldDefaults(
        Object module,
        Object fallbackModule,
        LeafConfigAccessor config
    ) throws IllegalAccessException {
        List<PendingValue> values = new ArrayList<>();
        collect(module, fallbackModule, config, true, null, values);
        applyAll(values);
    }

    /**
     * Binds a world override module. Only options present in the override file are read; the others keep
     * the value of the defaults, and the file is not completed with defaults.
     */
    static void bindWorldOverride(
        Object module,
        Object defaultsModule,
        LeafConfigAccessor config
    ) throws IllegalAccessException {
        List<PendingValue> values = new ArrayList<>();
        collect(module, defaultsModule, config, false, null, values);
        applyAll(values);
    }

    /**
     * Reads one module of a configuration.
     *
     * @param module          the module whose options are read; instance fields of a world module, or the
     *                        static fields of a global module
     * @param fallbackModule  supplies the value of options that are missing; {@code null} for a global
     *                        module, whose fallback is the current value of its static field
     * @param writeDefaults   whether the file is completed with defaults and comments; {@code false} for a
     *                        world override, which only reads the options it defines
     * @param restoreValues   when reloading, the first values of {@link HotReloadUnsupported} options, which
     *                        replace what the file says; {@code null} when binding for the first time
     */
    private static void collect(
        Object module,
        @Nullable Object fallbackModule,
        LeafConfigAccessor config,
        boolean writeDefaults,
        @Nullable Map<Field, Object> restoreValues,
        List<PendingValue> values
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        String basePath = ConfigPaths.modulePath(moduleClass);
        if (writeDefaults) {
            String sectionComment = config.pickStringRegionBased(moduleClass.getAnnotation(ConfigClassInfo.class).comments());
            if (sectionComment != null) {
                config.addComment(basePath, sectionComment);
            }
        }

        boolean moduleUnsupported = moduleClass.isAnnotationPresent(HotReloadUnsupported.class);
        for (Field field : moduleClass.getDeclaredFields()) {
            ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);
            if (configInfo == null || field.isAnnotationPresent(DoNotLoad.class)) {
                continue;
            }

            field.setAccessible(true);
            String comment = config.pickStringRegionBased(configInfo.comments());
            Object fallback = copyValue(field.get(fallbackModule));
            Object value = readValue(config, ConfigPaths.fieldPath(moduleClass, field), comment, field, fallback, writeDefaults);
            if (restoreValues != null && (moduleUnsupported || field.isAnnotationPresent(HotReloadUnsupported.class))) {
                value = restoreValues.get(field);
            }
            values.add(new PendingValue(Modifier.isStatic(field.getModifiers()) ? null : module, field, value));
        }
    }

    /**
     * Reads the values a reload would apply to an active world module from its loaded candidate. Options
     * that cannot be reloaded are restored to the first value of the active configuration.
     */
    static void collectWorldReload(
        Object module,
        Object loadedModule,
        @Nullable Map<Field, Object> initialValues,
        List<PendingValue> values
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        boolean moduleUnsupported = moduleClass.isAnnotationPresent(HotReloadUnsupported.class);
        for (Field field : moduleClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigInfo.class) == null || field.isAnnotationPresent(DoNotLoad.class)) {
                continue;
            }

            field.setAccessible(true);
            values.add(new PendingValue(module, field,
                moduleUnsupported || field.isAnnotationPresent(HotReloadUnsupported.class)
                    ? initialValues.get(field)
                    : field.get(loadedModule)));
        }
    }

    /** Copies the option values of a world defaults module to a module that inherits them. */
    static void applyWorldDefaults(Object module, Object defaultsModule) throws IllegalAccessException {
        for (Field field : module.getClass().getDeclaredFields()) {
            if (field.getAnnotation(DoNotLoad.class) != null
                || field.getAnnotation(ConfigInfo.class) == null) {
                continue;
            }

            field.setAccessible(true);
            field.set(module, copyValue(field.get(defaultsModule)));
        }
    }

    /** Captures the first values of the {@link HotReloadUnsupported} options of a global module. */
    static void captureGlobalInitialValues(Object module) throws IllegalAccessException {
        capture(module, INITIAL_GLOBAL_VALUES, null);
    }

    /**
     * Captures the first values of the {@link HotReloadUnsupported} options of a world module into the
     * configuration the module belongs to. Nothing is allocated when the module has no such option.
     */
    static void captureWorldInitialValues(Object module, LeafWorldConfig config) throws IllegalAccessException {
        capture(module, null, config);
    }

    private static void capture(
        Object module,
        @Nullable Map<Field, Object> globalValues,
        @Nullable LeafWorldConfig worldConfig
    ) throws IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        boolean moduleUnsupported = moduleClass.isAnnotationPresent(HotReloadUnsupported.class);
        Map<Field, Object> values = globalValues;
        for (Field field : moduleClass.getDeclaredFields()) {
            if (field.getAnnotation(ConfigInfo.class) == null || field.isAnnotationPresent(DoNotLoad.class)
                || (!moduleUnsupported && !field.isAnnotationPresent(HotReloadUnsupported.class))) {
                continue;
            }
            if (values == null) {
                values = worldConfig.initialValuesForCapture();
            }
            field.setAccessible(true);
            values.putIfAbsent(field, copyValue(field.get(Modifier.isStatic(field.getModifiers()) ? null : module)));
        }
    }

    /**
     * Reads one option. A value that is present is checked against the field type and rejected if it does
     * not fit; a missing or null value yields {@code fallback}.
     */
    private static Object readValue(
        LeafConfigAccessor config,
        String path,
        @Nullable String comment,
        Field field,
        Object fallback,
        boolean writeDefault
    ) {
        ConfigFile file = config.configFile;
        Object raw = file.get(path);
        Object value = raw == null ? fallback : ConfigValues.parse(field.getType(), file, path, raw);
        if (writeDefault) {
            Object fileFallback = fileValue(fallback);
            file.addDefault(path, fileFallback, comment);
            // addDefault puts back the value that was loaded from the file, or the default. A value that
            // was written to the in-memory file since (a migrated option) or an explicit null would be lost.
            file.set(path, raw == null ? fileFallback : raw);
        } else if (raw != null) {
            // An override is read without registering defaults. Saving it (a migrated or version-less file)
            // only writes what is registered or set, so the options it defines are set again, as they are.
            file.set(path, raw);
        }
        return value;
    }

    private static Object fileValue(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : value;
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        // Other primitive type values
        return value;
    }

    static void applyAll(List<PendingValue> values) throws IllegalAccessException {
        for (PendingValue value : values) {
            value.apply();
        }
    }

    /** A value that was read successfully and is yet to be written to a module field. */
    static final class PendingValue {

        private final @Nullable Object target;
        private final Field field;
        private final Object value;

        private PendingValue(@Nullable Object target, Field field, Object value) {
            this.target = target;
            this.field = field;
            this.value = copyValue(value);
        }

        void apply() throws IllegalAccessException {
            this.field.set(this.target, copyValue(this.value));
        }
    }
}
