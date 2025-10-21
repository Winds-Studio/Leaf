package org.dreeam.leaf.config;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.dreeam.leaf.config.annotations.Experimental;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class ConfigModules extends LeafConfig {

    private static final Set<ConfigModules> MODULES = new HashSet<>();

    public LeafGlobalConfig config;

    public ConfigModules() {
        this.config = LeafConfig.config();
    }

    public static void initModules() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<Field> enabledExperimentalModules = new ArrayList<>();
        List<Field> deprecatedModules = new ArrayList<>();

        Class<?>[] classes = LeafConfig.getClasses(LeafConfig.I_CONFIG_PKG).toArray(new Class[0]);
        ObjectArrays.quickSort(classes, Comparator.comparing(Class::getSimpleName));
        for (Class<?> clazz : classes) {
            ConfigModules module = (ConfigModules) clazz.getConstructor().newInstance();
            module.onLoaded();

            MODULES.add(module);
            for (Field field : getAnnotatedStaticFields(clazz, Experimental.class)) {
                if (!(field.get(null) instanceof Boolean enabled)) continue;
                if (enabled) {
                    enabledExperimentalModules.add(field);
                }
            }
            for (Field field : getAnnotatedStaticFields(clazz, Deprecated.class)) {
                if (!(field.get(null) instanceof Boolean enabled)) continue;
                if (enabled) {
                    deprecatedModules.add(field);
                }
            }
        }

        if (!enabledExperimentalModules.isEmpty()) {
            LeafConfig.LOGGER.warn("You have following experimental module(s) enabled: {}, please proceed with caution!", formatModules(enabledExperimentalModules));
        }

        if (!deprecatedModules.isEmpty()) {
            LeafConfig.LOGGER.warn("The following enabled module(s) has been deprecated: {}, please proceed with caution!", formatModules(deprecatedModules));
        }
    }

    private static List<String> formatModules(List<Field> modules) {
        return modules.stream().map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getName()).toList();
    }

    public static void loadAfterBootstrap() {
        for (ConfigModules module : MODULES) {
            module.onPostLoaded();
        }

        // Save config to disk
        try {
            LeafConfig.config().saveConfig();
        } catch (Exception e) {
            LeafConfig.LOGGER.error("Failed to save config file!", e);
        }
    }

    private static List<Field> getAnnotatedStaticFields(Class<?> clazz, Class<? extends Annotation> annotation) {
        List<Field> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation) && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }

        return fields;
    }

    public static void clearModules() {
        MODULES.clear();
    }

    public abstract void onLoaded();

    public void onPostLoaded() {
    }
}
